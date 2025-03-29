package appu;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

public class ForageConfig
{
    public static final File JSON = new File(System.getProperty("user.home"), "forageroblox.json");
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static ArrayList<String> getTokens()
    {
        if (!JSON.exists()) return null;
        try { return objectMapper.readValue(JSON, ConfigData.class).robloxTokens; } catch (IOException e) { throw new RuntimeException(e); }
    }

    public static void setTokens(ArrayList<String> tokens)
    {
        ConfigData data = new ConfigData();
        data.proxies = getProxies();
        data.robloxTokens = tokens;

        try
        {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(JSON, data);
        }

        catch (IOException e)
        {
            System.err.println(e.getMessage());
        }
    }

    public static ArrayList<String> getProxies()
    {
        if (!JSON.exists()) return new ArrayList<>(Collections.singletonList("localhost"));
        try { return objectMapper.readValue(JSON, ConfigData.class).proxies; } catch (IOException e) { throw new RuntimeException(e); }
    }

    public static void setProxies(ArrayList<String> proxies)
    {
        ConfigData data = new ConfigData();
        data.robloxTokens = getTokens();
        data.proxies = proxies;

        try
        {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(JSON, data);
        }

        catch (IOException e)
        {
            System.err.println(e.getMessage());
        }
    }

    static class ConfigData
    {
        @JsonProperty(".ROBLOSECURITY") public ArrayList<String> robloxTokens;
        /* Every two roblox tokens need a proxy to avoid rate-limits */
        @JsonProperty("proxies") public ArrayList<String> proxies;
    }
}
