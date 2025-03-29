package appu;

import appu.utils.ForageUtils;
import appu.utils.RobloxAPI;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ForageRoblox
{
    public ArrayList<Long> userIds, placeIds, remaining = null;
    public ArrayList<String> userNames, placeNames;
    public ArrayList<BufferedImage> userImages;
    public boolean logOutput;

    @SuppressWarnings("unchecked")
    public ForageRoblox(ArrayList<?> userNamesOrIds, ArrayList<?> placeNamesOrIds, boolean logOutput)
    {
        if (userNamesOrIds.get(0) instanceof String)
        {
            this.userNames = (ArrayList<String>) userNamesOrIds;
            this.userIds = userNames.stream().map(RobloxAPI::getUserId).collect(Collectors.toCollection(ArrayList::new));
        } else {
            this.userIds = (ArrayList<Long>) userNamesOrIds;
            this.userNames = userIds.stream().map(RobloxAPI::getUserName).collect(Collectors.toCollection(ArrayList::new));
        }

        if (placeNamesOrIds.get(0) instanceof String)
        {
            this.placeNames = (ArrayList<String>) placeNamesOrIds;
            this.placeIds = placeNames.stream().map(RobloxAPI::getPlaceId).collect(Collectors.toCollection(ArrayList::new));
        } else {
            this.placeIds = (ArrayList<Long>) placeNamesOrIds;
            this.placeNames = placeIds.stream().map(RobloxAPI::getPlaceName).collect(Collectors.toCollection(ArrayList::new));
        }

        this.logOutput = logOutput;
    }

    public ArrayList<PlayerData> go()
    {
        AtomicBoolean finished = new AtomicBoolean(false);
        ArrayList<PlayerData> playerDatas = checkUserStatuses();

        if (playerDatas == null || !playerDatas.isEmpty())
            return playerDatas;

        userImages = RobloxAPI.getAvatarsFromIds(userIds);
        AtomicInteger i = new AtomicInteger(0);
        remaining = new ArrayList<>(userIds);

        for (long placeId : placeIds)
        {
            RobloxAPI.getAllServers(placeId, holder ->
            {
                ArrayList<String> tokens = new ArrayList<>();

                for (RobloxAPI.PublicServerData server : holder.servers)
                {
                    for (String token : server.playerTokens)
                        tokens.add(token + " - " + server.id);
                }

                HashMap<BufferedImage, String> images = RobloxAPI.getAvatars(tokens);
                int found = 0;

                imageSearch: for (BufferedImage image : images.keySet())
                {
                    for (BufferedImage target : userImages)
                    {
                        if (ForageUtils.compare(target, image))
                        {
                            String name = userNames.get(found),
                                   place = placeNames.get(i.get()),
                                   server = images.get(image);

                            playerDatas.add(new PlayerData(name, place, server, placeIds.get(i.get())));

                            if (logOutput)
                                System.out.println((found == 0 ? "\n" : "") + " - " + name + " is at " + place + " (" + placeIds.get(i.get()) + ") in server " + server + ".");

                            if (remaining.size() == 1) remaining.clear();
                            else remaining.remove(found);
                            finished.set(remaining.isEmpty());
                            RobloxAPI.stop = finished.get();
                            found++;
                            if (finished.get()) break imageSearch;
                            break;
                        }
                    }
                }
            }, logOutput);

            if (finished.get()) break;
            i.incrementAndGet();
        }

        if (!finished.get() && logOutput)
            System.out.println("\nCould not locate players.");

        return playerDatas;
    }

    /**
     * This checks for cases whether the users are
     * offline, whether they are not playing a game,
     * whether one of them has their "Who can join?"
     * public, and so on; all special cases for efficiency.
     */
    private ArrayList<PlayerData> checkUserStatuses()
    {
        ArrayList<RobloxAPI.UserPresence> presences = RobloxAPI.getPresences(userIds);
        ArrayList<PlayerData> playerDatas = new ArrayList<>();
        assert presences != null;
        boolean allPublic = true;

        for (RobloxAPI.UserPresence presence : presences)
        {
            if (presence.gameId == null)
            {
                allPublic = false;
                break;
            }
        }

        if (allPublic)
        {
            for (RobloxAPI.UserPresence presence : presences)
            {
                PlayerData playerData = new PlayerData(
                        RobloxAPI.getUserName(presence.userId),
                        presence.lastLocation, presence.gameId,
                        presence.rootPlaceId);

                if (logOutput)
                    System.out.println(" - " + playerData.name + " is at " + playerData.gameName + " (" + playerData.rootPlaceId + ") in server " + playerData.serverId + ".");

                playerDatas.add(playerData);
            }
        }

        else for (RobloxAPI.UserPresence presence : presences)
        {
            if (presence.gameId != null)
            {
                PlayerData playerData = new PlayerData(
                        RobloxAPI.getUserName(presence.userId),
                        presence.lastLocation, presence.gameId,
                        presence.rootPlaceId);

                if (logOutput)
                    System.out.println(" - " + playerData.name + " is at " + playerData.gameName + " (" + playerData.rootPlaceId + ") in server " + playerData.serverId + ".");

                userIds.remove(presence.userId);
            }
        }

        boolean allNotInGame = true;

        for (RobloxAPI.UserPresence presence : presences)
        {
            if (presence.userPresenceType == 2)
            {
                allNotInGame = false;
                break;
            }
        }

        if (allNotInGame)
        {
            if (logOutput)
            {
                if (userIds.size() == 1)
                    System.out.println(RobloxAPI.getUserName(userIds.get(0)) + " is not in a game.");
                else
                    System.out.println("The selected users are not in games.");
            }

            return null;
        }

        else for (RobloxAPI.UserPresence presence : presences)
        {
            if (presence.userPresenceType != 2)
            {
                if (logOutput)
                    System.out.println(RobloxAPI.getUserName(presence.userId) + " is not in a game.");

                userIds.remove(presence.userId);
            }
        }

        return playerDatas;
    }

    public static class PlayerData
    {
        public final String name, gameName, serverId;
        public final long rootPlaceId;

        public PlayerData(String name, String gameName, String serverId, long rootPlaceId)
        {
            this.name = name;
            this.gameName = gameName;
            this.serverId = serverId;
            this.rootPlaceId = rootPlaceId;
        }
    }
}
