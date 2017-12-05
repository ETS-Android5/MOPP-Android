package ee.ria.DigiDoc.android.utils;

import android.os.Bundle;
import android.os.Parcelable;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;

public final class BundleUtils {

    private BundleUtils() {}

    public static <T extends Parcelable> void putParcelableImmutableList(Bundle bundle, String key,
                                                                         ImmutableList<T> value) {
        ArrayList<T> arrayList = new ArrayList<>(value.size());
        arrayList.addAll(value);
        bundle.putParcelableArrayList(key, arrayList);
    }

    @SuppressWarnings("ConstantConditions")
    public static <T extends Parcelable> ImmutableList<T> getParcelableImmutableList(Bundle bundle,
                                                                                     String key) {
        return ImmutableList.copyOf(bundle.getParcelableArrayList(key));
    }
}
