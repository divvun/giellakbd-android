package so.brendan.locale;

import android.content.Context;
import android.content.res.Resources;

public final class ExtraLocaleUtil {
    private ExtraLocaleUtil() {}

    private static Context sContext;
    private static Resources sResources;

    public static void init(Context context) {
        sContext = context;
        sResources = context.getResources();
    }

    public static String getDisplayNameForLocaleString(String locale) {
        int resId = sResources.getIdentifier("subtype_" + locale,
                "string", sContext.getPackageName());

        return getDisplayNameForLocaleString(resId);
    }

    public static String getDisplayNameForLocaleString(int resId) {
        return sContext.getResources().getString(resId);
    }
}
