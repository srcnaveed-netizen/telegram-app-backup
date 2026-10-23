package org.telegram.messenger;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class StickerUtils {

    public static File ensurePlaceholderExtracted(int index) {
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return null;
        }
        try {
            File dir = new File(context.getFilesDir(), "stickers_placeholders");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            String fileName = "placeholder_" + index + ".tgs";
            File dest = new File(dir, fileName);
            if (dest.exists() && dest.length() > 0) {
                return dest;
            }
            try (InputStream in = context.getAssets().open("stickers_placeholders/" + fileName);
                 FileOutputStream out = new FileOutputStream(dest)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
            }
            return dest;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    public static File ensureStickerExtracted(String fileName) {
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return null;
        }
        try {
            File dir = new File(context.getFilesDir(), "stickers");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File dest = new File(dir, fileName);
            if (dest.exists() && dest.length() > 0) {
                return dest;
            }
            try (InputStream in = context.getAssets().open("stickers/" + fileName);
                 FileOutputStream out = new FileOutputStream(dest)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();
            }
            return dest;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    public static String getEmojiForIndex(int index) {
        switch (index) {
            case 0: return "😢";
            case 1: return "🧐";
            case 2: return "😋";
            case 3: return "😁";
            case 4: return "🌚";
            case 5: return "🔗";
            case 6: return "💻";
            case 7: return "😒";
            case 8: return "💪";
            case 9: return "😃";
            case 10: return "😎";
            case 11: return "🖼";
            case 12: return "🎩";
            default: return "⭐️";
        }
    }

    public static int getIndexForEmoji(String emoji) {
        if (emoji == null) return -1;
        switch (emoji) {
            case "😢": return 0;
            case "🧐": return 1;
            case "😋": return 2;
            case "😁": return 3;
            case "🌚": return 4;
            case "🔗": return 5;
            case "💻": return 6;
            case "😒": return 7;
            case "💪": return 8;
            case "😃": return 9;
            case "😎": return 10;
            case "🖼": return 11;
            case "🎩": return 12;
            default: return -1;
        }
    }
}
