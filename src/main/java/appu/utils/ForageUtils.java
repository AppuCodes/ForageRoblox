package appu.utils;

import java.awt.image.BufferedImage;
import java.util.ArrayList;

public class ForageUtils
{
    public static boolean compare(BufferedImage imgA, BufferedImage imgB)
    {
        int width  = imgA.getWidth(), height = imgA.getHeight();

        for (int x = 0; x < width; x++)
        {
            for (int y = 0; y < height; y++)
            {
                if (imgA.getRGB(x, y) != imgB.getRGB(x, y))
                    return false;
            }
        }

        return true;
    }

    public static <T> ArrayList<ArrayList<T>> chopList(ArrayList<T> list, int length)
    {
        ArrayList<ArrayList<T>> parts = new ArrayList<>();
        int n = list.size();

        for (int i = 0; i < n; i += length)
            parts.add(new ArrayList<>(list.subList(i, Math.min(n, i + length))));

        return parts;
    }
}
