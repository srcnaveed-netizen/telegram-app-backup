package org.telegram.messenger;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class PremiumVideoUtils {

    public static File ensurePremiumVideoExtracted(String typeString) {
        Context context = ApplicationLoader.applicationContext;
        if (context == null || typeString == null) {
            return null;
        }
        try {
            File dir = new File(context.getCacheDir(), "premium_videos");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            String fileName = typeString + ".mp4";
            File dest = new File(dir, fileName);
            if (dest.exists() && dest.length() > 0) {
                return dest;
            }
            try (InputStream in = context.getAssets().open("premium_videos/" + fileName);
                 FileOutputStream out = new FileOutputStream(dest)) {
                byte[] buffer = new byte[16384];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
            }
            return dest;
        } catch (Exception e) {
            return null;
        }
    }
}
