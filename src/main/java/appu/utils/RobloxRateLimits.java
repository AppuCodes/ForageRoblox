package appu.utils;

import java.util.HashMap;

public class RobloxRateLimits
{
    private static final HashMap<String, RobloxRateLimits> rateLimits = new HashMap<>();
    public static final long RATE_LIMIT_MS = 1100;
    private long lastCall = 0;

    public void callPageServers()
    {
        lastCall = System.currentTimeMillis();
    }

    public boolean rateLimited()
    {
        return System.currentTimeMillis() - lastCall <= RATE_LIMIT_MS;
    }

    public void sleepForRateLimit()
    {
        long now = System.currentTimeMillis();
        long sleepTime = RATE_LIMIT_MS - (now - lastCall);

        if (sleepTime > 0)
            try { Thread.sleep(sleepTime); } catch (InterruptedException ignored) {}
    }

    /* Roblox has a rate-limit for each 2 tokens an IP address. */
    public static RobloxRateLimits get(String token)
    {
        if (rateLimits.containsKey(token))
            return rateLimits.get(token);

        RobloxRateLimits rateLimitsHelper = new RobloxRateLimits();
        rateLimits.put(token, rateLimitsHelper);
        return rateLimitsHelper;
    }
}
