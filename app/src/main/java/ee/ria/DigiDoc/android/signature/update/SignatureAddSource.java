package ee.ria.DigiDoc.android.signature.update;

import java.io.File;
import java.nio.charset.StandardCharsets;

import javax.inject.Inject;

import ee.ria.DigiDoc.R;
import ee.ria.DigiDoc.android.main.settings.SettingsDataStore;
import ee.ria.DigiDoc.android.model.idcard.IdCardData;
import ee.ria.DigiDoc.android.model.idcard.IdCardService;
import ee.ria.DigiDoc.android.model.idcard.IdCardSignResponse;
import ee.ria.DigiDoc.android.signature.data.SignatureContainerDataSource;
import ee.ria.DigiDoc.android.signature.update.idcard.IdCardRequest;
import ee.ria.DigiDoc.android.signature.update.idcard.IdCardResponse;
import ee.ria.DigiDoc.android.signature.update.mobileid.MobileIdOnSubscribe;
import ee.ria.DigiDoc.android.signature.update.mobileid.MobileIdRequest;
import ee.ria.DigiDoc.android.signature.update.mobileid.MobileIdResponse;
import ee.ria.DigiDoc.android.signature.update.smartid.SmartIdOnSubscribe;
import ee.ria.DigiDoc.android.signature.update.smartid.SmartIdRequest;
import ee.ria.DigiDoc.android.signature.update.smartid.SmartIdResponse;
import ee.ria.DigiDoc.android.utils.navigator.Navigator;
import ee.ria.DigiDoc.idcard.CodeVerificationException;
import ee.ria.DigiDoc.mobileid.dto.response.MobileCreateSignatureSessionStatusResponse;
import ee.ria.DigiDoc.smartid.dto.response.SessionStatusResponse;
import ee.ria.DigiDoc.sign.SignedContainer;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import okio.ByteString;

final class SignatureAddSource {

    private final Navigator navigator;
    private final SignatureContainerDataSource signatureContainerDataSource;
    private final SettingsDataStore settingsDataStore;
    private final IdCardService idCardService;

    private static final String EMPTY_VALUE = "";

    @Inject SignatureAddSource(Navigator navigator,
                               SignatureContainerDataSource signatureContainerDataSource,
                               SettingsDataStore settingsDataStore, IdCardService idCardService) {
        this.navigator = navigator;
        this.signatureContainerDataSource = signatureContainerDataSource;
        this.settingsDataStore = settingsDataStore;
        this.idCardService = idCardService;
    }

    Observable<Result.SignatureAddResult> show(int method) {
        if (method == R.id.signatureUpdateSignatureAddMethodMobileId) {
            return Observable.just(Result.SignatureAddResult.show(method));
        } else if (method == R.id.signatureUpdateSignatureAddMethodSmartId) {
            return Observable.just(Result.SignatureAddResult.show(method));
        } else if (method == R.id.signatureUpdateSignatureAddMethodIdCard) {
            return idCardService.data()
                    .map(dataResponse -> {
                        if (dataResponse.error() == null) {
                            return Result.SignatureAddResult
                                    .method(method, IdCardResponse.data(dataResponse));
                        } else {
                            return Result.SignatureAddResult.failure(dataResponse.error());
                        }
                    })
                    .startWithItem(Result.SignatureAddResult.method(method, IdCardResponse.initial()));
        } else {
            throw new IllegalArgumentException("Unknown method " + method);
        }
    }

    Observable<? extends SignatureAddResponse> sign(File containerFile,
                                                    SignatureAddRequest request) {
        if (request instanceof MobileIdRequest) {
            MobileIdRequest mobileIdRequest = (MobileIdRequest) request;
            if (mobileIdRequest.rememberMe()) {
                settingsDataStore.setPhoneNo(mobileIdRequest.phoneNo());
                settingsDataStore.setPersonalCode(mobileIdRequest.personalCode());
            } else {
                settingsDataStore.setPhoneNo(EMPTY_VALUE);
                settingsDataStore.setPersonalCode(EMPTY_VALUE);
            }
            return signatureContainerDataSource
                    .get(containerFile)
                    .flatMapObservable(container ->
                            Observable.create(new MobileIdOnSubscribe(navigator, container,
                                    settingsDataStore.getUuid(), mobileIdRequest.personalCode(),
                                    mobileIdRequest.phoneNo())))
                    .switchMap(response -> {
                        String signature = response.signature();
                        if (signature != null) {
                            return signatureContainerDataSource
                                    .addSignature(containerFile, signature)
                                    .toObservable()
                                    .map(MobileIdResponse::success)
                                    .subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .startWithItem(response);
                        } else {
                            return Observable.just(response);
                        }
                    })
                    .startWithItem(MobileIdResponse
                            .status(MobileCreateSignatureSessionStatusResponse.ProcessStatus.OK));
        } else if (request instanceof SmartIdRequest) {
            SmartIdRequest smartIdRequest = (SmartIdRequest) request;
            if (smartIdRequest.rememberMe()) {
                settingsDataStore.setCountry(smartIdRequest.country());
                settingsDataStore.setSidPersonalCode(smartIdRequest.personalCode());
            } else {
                settingsDataStore.setCountry(EMPTY_VALUE);
                settingsDataStore.setSidPersonalCode(EMPTY_VALUE);
            }
            return signatureContainerDataSource
                    .get(containerFile)
                    .flatMapObservable(container ->
                            Observable.create(new SmartIdOnSubscribe(navigator, container,
                                    settingsDataStore.getUuid(), smartIdRequest.personalCode(),
                                    smartIdRequest.country())))
                    .switchMap(response ->
                            Observable.just(response)
                    )
                    .startWithItem(SmartIdResponse
                            .status(SessionStatusResponse.ProcessStatus.OK));
        } else if (request instanceof IdCardRequest) {
            IdCardRequest idCardRequest = (IdCardRequest) request;
            return signatureContainerDataSource
                    .get(containerFile)
                    .flatMap(container ->
                            idCardService.data()
                                    .filter(dataResponse -> dataResponse.token() != null)
                                    .switchMapSingle(dataResponse ->
                                            idCardService.sign(dataResponse.token(), container,
                                                    idCardRequest.pin2())).firstOrError())
                    .map(IdCardResponse::success)
                    .toObservable()
                    .onErrorResumeNext(error -> {
                        if (error instanceof CodeVerificationException) {
                            return idCardService.data()
                                    .filter(dataResponse -> dataResponse.data() != null)
                                    .switchMap(dataResponse -> {
                                        IdCardData data = dataResponse.data();
                                        if (data != null && data.pin2RetryCount() > 0) {
                                            return Observable.just(
                                                    IdCardResponse.sign(IdCardSignResponse.clear(
                                                            error, data, idCardRequest.token())),
                                                    IdCardResponse.sign(IdCardSignResponse.failure(
                                                            error, data, idCardRequest.token())));
                                        }
                                        return Observable.error(error);
                                    });
                        }

                        return Observable.error(error);
                    })
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .startWithItem(IdCardResponse.sign(IdCardSignResponse.activity()));
        } else {
            throw new IllegalArgumentException("Unknown request " + request);
        }
    }

    public Single<SignedContainer> sign(String signatureValue, byte[] dataToSign, SignedContainer container) {
        return Single
                .fromCallable(() -> container.sign(ByteString.of(dataToSign),
                        signData -> ByteString.encodeUtf8(signatureValue)))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }
}
