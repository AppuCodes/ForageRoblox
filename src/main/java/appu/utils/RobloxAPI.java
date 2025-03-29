package appu.utils;

import appu.ForageConfig;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.*;
import java.net.Authenticator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class RobloxAPI
{
    private static final HashMap<Proxy, OkHttpClient> clients = new HashMap<>();
    private static final MediaType JSON = MediaType.get("application/json");
    private static final ArrayList<Proxy> proxyObjects = new ArrayList<>();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    public static ArrayList<String> proxies = ForageConfig.getProxies();
    public static volatile boolean stop = false;
    private static int rotation = 0;

    public static long getPlaceId(String placeName)
    {
        Request request = new Request.Builder()
                .url("https://apis.roblox.com/search-api/omni-search?searchQuery=" + placeName.replace(" ", "+") + "&sessionId=null")
                .build();

        try (Response response = getClient().newCall(request).execute())
        {
            assert response.body() != null;
            return objectMapper.readValue(response.body().string(), SearchResponse.class).searchResults.get(0).contents.get(0).rootPlaceId;
        }

        catch (Exception ignored) { return 0; }
    }

    public static long getUserId(String playerName)
    {
        Request request = new Request.Builder()
                .url("https://apis.roblox.com/search-api/omni-search?verticalType=user&searchQuery=" + playerName + "&sessionId=null")
                .build();

        try (Response response = getClient().newCall(request).execute())
        {
            assert response.body() != null;
            return objectMapper.readValue(response.body().string(), SearchResponse.class).searchResults.get(0).contents.get(0).contentId;
        }

        catch (Exception ignored) { return 0; }
    }

    public static String getUserName(long playerId)
    {
        Request request = new Request.Builder()
                .url("https://users.roblox.com/v1/users/" + playerId)
                .build();

        try (Response response = getClient().newCall(request).execute())
        {
            assert response.body() != null;
            return objectMapper.readTree(response.body().string()).get("name").asText();
        }

        catch (Exception ignored) { return playerId + ""; }
    }

    public static HashMap<BufferedImage, String> getAvatars(ArrayList<String> tokens)
    {
        HashMap<BufferedImage, String> images = new HashMap<>();
        StringBuilder json = new StringBuilder("[");
        ArrayList<String> remaining = null;

        if (tokens.size() <= 100)
        {
            for (String token : tokens)
            {
                json.append("{\"requestId\":\"0:").append(token.split(" - ")[0])
                        .append(":AvatarHeadshot:150x150:webp:regular\",\"type\":\"AvatarHeadShot\",")
                        .append("\"targetId\":0,").append("\"token\":\"")
                        .append(token.split(" - ")[0]).append("\",").append("\"format\":\"webp\",")
                        .append("\"size\":\"150x150\"},");
            }
        }

        /* The server only accepts 100 tokens at a time, so we need to split. */
        else
        {
            ArrayList<ArrayList<String>> chopped = ForageUtils.chopList(tokens, 100);

            for (String token : chopped.get(0))
            {
                json.append("{\"requestId\":\"0:").append(token.split(" - ")[0])
                        .append(":AvatarHeadshot:150x150:webp:regular\",\"type\":\"AvatarHeadShot\",")
                        .append("\"targetId\":0,").append("\"token\":\"")
                        .append(token.split(" - ")[0]).append("\",").append("\"format\":\"webp\",")
                        .append("\"size\":\"150x150\"},");
            }

            remaining = chopped.get(1);
        }

        json.deleteCharAt(json.length() - 1).append("]");
        RequestBody body = RequestBody.create(json.toString(), JSON);

        Request request = new Request.Builder()
                .url("https://thumbnails.roblox.com/v1/batch")
                .post(body).build();

        try (Response response = getClient().newCall(request).execute())
        {
            int i = 0;
            assert response.body() != null;

            for (JsonNode node : objectMapper.readTree(response.body().string()).path("data"))
            {
                try
                {
                    images.put(ImageIO.read(URI.create(node.path("imageUrl").asText()).toURL()), tokens.get(i).split(" - ")[1]);
                } catch (IllegalArgumentException ignored) {}
                i++;
            }

            if (remaining != null)
                images.putAll(getAvatars(remaining));
        }

        catch (Exception e) { System.err.println(e.getMessage()); }
        return images;
    }

    public static void getAllServers(long placeId, Consumer<DataHolder> loop, boolean logOutput)
    {
        String nextPageCursor = "null"; int i = 0, servers = 0, prevProgress = -99;
        ArrayList<String> tokens = ForageConfig.getTokens();
        UniverseInfo info = getServerInfo(placeId);
        int maxServers = info.playing / info.maxPlayers;
        DataHolder prevHolder = null;

        while (!nextPageCursor.equals("null") || i == 0)
        {
            DataHolder holder = getPageServers(placeId, nextPageCursor, info.maxPlayers, tokens);
            nextPageCursor = holder.nextPageCursor;
            if (stop) break;
            if (!nextPageCursor.equals("null")) CompletableFuture.runAsync(() -> loop.accept(holder));
            i++; servers += holder.servers.size();
            double percentage = Math.min(servers / (double) maxServers, 0.99);
            int progress = (int) (percentage * 100);

            if (progress - prevProgress >= 13 && progress != 0 && logOutput)
            {
                System.out.print(progress + "%.. ");
                prevProgress = progress;
            }

            if (nextPageCursor.equals("null")) prevHolder = holder;
        }

        if (!stop) loop.accept(prevHolder);
        stop = false;
    }

    public static DataHolder getPageServers(long placeId, String nextPageCursor, int serverSize, ArrayList<String> tokens)
    {
        int remainingTries = serverSize <= 5 ? 1 : calculateRequests(serverSize);
        ArrayList<PublicServerData> servers = new ArrayList<>();
        boolean foundAllPlayers = false;
        String cursor = "null";

        /*
         * The API only returns 5 players per server at a time. However,
         * if you keep sending requests, eventually you would find most
         * of the players in servers as the order is randomized each
         * time. The more proxies and .ROBLOSECURITYs there are, the
         * faster this search is. It is also recommended to enter more
         * than one username for a greater chance finding your targets.
         */
        while (!foundAllPlayers && remainingTries != 0 && !stop)
        {
            rotation++; if (rotation == tokens.size()) rotation = 0;
            String token = tokens.get(rotation);

            if (rotation == 0 && RobloxRateLimits.get(token).rateLimited())
                RobloxRateLimits.get(token).sleepForRateLimit();

            Request request = new Request.Builder()
                    .url("https://games.roblox.com/v1/games/" + placeId + "/servers/Public?limit=10" + (!nextPageCursor.equals("null") ? "&cursor=" + nextPageCursor : ""))
                    .addHeader("Cookie", ".ROBLOSECURITY=" + token)
                    .build();

            try (Response response = getClient(getProxy(rotation)).newCall(request).execute())
            {
                RobloxRateLimits.get(token).callPageServers();
                assert response.body() != null;
                String responseBody = response.body().string();

                if (responseBody.contains("\"message\":\"The place is invalid.\""))
                {
                    stop = true;
                    System.err.println("The place is invalid.");
                }

                PublicServerResponse serverResponse = objectMapper.readValue(responseBody, PublicServerResponse.class);
                cursor = (serverResponse.nextPageCursor == null ? "null" : serverResponse.nextPageCursor);
                int maxPlayingOnServers = 0;

                if (servers.isEmpty())
                    servers.addAll(serverResponse.data);
                else
                {
                    for (PublicServerData server : servers)
                    {
                        for (PublicServerData responseServer : serverResponse.data)
                        {
                            if (responseServer.id.equals(server.id))
                            {
                                for (String playerToken : responseServer.playerTokens)
                                {
                                    if (!server.playerTokens.contains(playerToken))
                                        server.playerTokens.add(playerToken);
                                }
                            }
                        }
                    }
                }

                foundAllPlayers = true;

                for (PublicServerData server : servers)
                {
                    if (maxPlayingOnServers < server.playing)
                        maxPlayingOnServers = server.playing;

                    for (PublicServerData responseServer : serverResponse.data)
                    {
                        if (responseServer.id.equals(server.id) && server.playerTokens.size() < Math.min(responseServer.playing, server.playing))
                        {
                            foundAllPlayers = false;
                            break;
                        }
                    }
                }

                int tries = maxPlayingOnServers <= 5 ? 1 : calculateRequests(maxPlayingOnServers);
                if (tries < remainingTries) remainingTries = tries;
                remainingTries--;
            }
            catch (Exception ignored) {}
        }

        DataHolder holder = new DataHolder();
        holder.servers = servers;
        holder.nextPageCursor = cursor;
        return holder;
    }

    public static ArrayList<BufferedImage> getAvatarsFromIds(ArrayList<Long> userIds)
    {
        ArrayList<BufferedImage> images = new ArrayList<>();
        StringBuilder json = new StringBuilder("[");

        for (long user : userIds)
        {
            json.append("{\"requestId\":\"").append(user)
                    .append(":undefined:AvatarHeadshot:150x150:webp:regular\",\"type\":\"AvatarHeadShot\",")
                    .append("\"targetId\":\"").append(user).append("\",")
                    .append("\"format\":\"webp\",")
                    .append("\"size\":\"150x150\"},");
        }

        json.deleteCharAt(json.length() - 1).append("]");
        RequestBody body = RequestBody.create(json.toString(), JSON);

        Request request = new Request.Builder()
                .url("https://thumbnails.roblox.com/v1/batch")
                .post(body).build();

        try (Response response = getClient().newCall(request).execute())
        {
            assert response.body() != null;

            for (JsonNode node : objectMapper.readTree(response.body().string()).path("data"))
            {
                try
                {
                    images.add(ImageIO.read(URI.create(node.path("imageUrl").asText()).toURL()));
                } catch (IllegalArgumentException ignored) {}
            }
        }

        catch (Exception e)
        {
            if (!e.getMessage().contains("500"))
                System.err.println(e.getMessage());
        }

        return images;
    }

    public static UniverseInfo getServerInfo(long placeId)
    {
        UniverseInfo info = new UniverseInfo();
        long universeId = 0;

        Request fetchUniverseId = new Request.Builder()
                .url("https://apis.roblox.com/universes/v1/places/" + placeId + "/universe")
                .build();

        /* to get universeId */
        try (Response response = getClient().newCall(fetchUniverseId).execute())
        {
            assert response.body() != null;
            universeId = objectMapper.readTree(response.body().string()).path("universeId").asLong();
        }
        catch (Exception ignored) {}

        Request fetchInfo = new Request.Builder()
                .url("https://games.roblox.com/v1/games?universeIds=" + universeId)
                .build();

        /* to get universe info */
        try (Response response = getClient().newCall(fetchInfo).execute())
        {
            assert response.body() != null;
            JsonNode node = objectMapper.readTree(response.body().string()).path("data").get(0);
            info.maxPlayers = node.path("maxPlayers").asInt();
            info.playing = node.path("playing").asInt();
            info.name = node.path("name").asText();
        }
        catch (Exception ignored) {}
        return info;
    }

    public static String getPlaceName(long placeId)
    {
        return getServerInfo(placeId).name;
    }

    public static ArrayList<UserPresence> getPresences(ArrayList<Long> userIds)
    {
        RequestBody body = RequestBody.create("{\"userIds\": " + userIds + "}", JSON);

        Request request = new Request.Builder()
                .url("https://presence.roblox.com/v1/presence/users")
                .post(body).build();

        try (Response response = getClient().newCall(request).execute())
        {
            assert response.body() != null;
            return new ArrayList<>(objectMapper.readValue(
                    objectMapper.readTree(response.body().string()).get("userPresences").toString(),
                    new TypeReference<List<UserPresence>>() {}
            ));
        }
        catch (Exception ignored) {}
        return null;
    }

    private static Proxy getProxy(int rotation)
    {
        if (proxies.isEmpty() || (proxies.size() == 1 && proxies.get(0).equals("localhost"))) return Proxy.NO_PROXY;

        try
        {
            if (proxyObjects.isEmpty())
            {
                /* if the proxy has credentials */
                if (proxies.get(1).split(":").length > 2)
                {
                    Authenticator.setDefault(new Authenticator()
                    {
                        @Override
                        protected PasswordAuthentication getPasswordAuthentication()
                        {
                            return new PasswordAuthentication(proxies.get(1).split(":")[2], proxies.get(1).split(":")[3].toCharArray());
                        }
                    });
                }

                for (String prox : proxies)
                {
                    if (prox.equals("localhost"))
                    {
                        proxyObjects.add(Proxy.NO_PROXY);
                        continue;
                    }

                    Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(prox.split(":")[0], Integer.parseInt(prox.split(":")[1])));
                    proxyObjects.add(proxy);
                }
            }
        }
        catch (Exception e) { System.err.println(e.getMessage()); }
        return (rotation / 2) >= proxyObjects.size() ? Proxy.NO_PROXY : proxyObjects.get(rotation / 2);
    }

    private static OkHttpClient getClient() { return getClient(Proxy.NO_PROXY); }

    private static OkHttpClient getClient(Proxy proxy)
    {
        if (clients.containsKey(proxy))
            return clients.get(proxy);
        else
        {
            OkHttpClient client = proxy.equals(Proxy.NO_PROXY) ? new OkHttpClient()
                    : new OkHttpClient.Builder().proxy(proxy).build();
            clients.put(proxy, client);
            return client;
        }
    }

    /**
     * Based on the Coupon Collector's Problem formula.
     * @param n Total number of players in the server
     * @return The number of requests needed to collect all players
     */
    public static int calculateRequests(int n)
    {
        return (int) Math.ceil((n * Math.log(n)) / 5);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SearchResponse
    {
        public ArrayList<SearchResult> searchResults;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SearchResult
    {
        public ArrayList<SearchContent> contents;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SearchContent
    {
        public long rootPlaceId;
        public long contentId;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class PublicServerResponse
    {
        public String nextPageCursor;
        public ArrayList<PublicServerData> data;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PublicServerData
    {
        public String id;
        public int playing;
        public ArrayList<String> playerTokens;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserPresence
    {
        public String gameId, lastLocation;
        public long userId, rootPlaceId;
        public int userPresenceType;
    }

    public static class DataHolder
    {
        public ArrayList<PublicServerData> servers;
        public String nextPageCursor;
    }

    public static class UniverseInfo
    {
        public int maxPlayers = 16, playing = 512;
        public String name = null;
    }
}
