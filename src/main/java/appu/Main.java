package appu;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Scanner;
import java.util.stream.Collectors;

public class Main
{
    public static void main(String[] args)
    {
        Scanner scanner = new Scanner(System.in);
        ForageRoblox searcher;

        if (ForageConfig.getTokens() == null)
        {
            System.out.print("Welcome! To proceed, please enter your .ROBLOSECURITYs: ");
            String token = scanner.nextLine();
            ForageConfig.setTokens(token.contains(",") ?
                    Arrays.stream(token.split(",")).collect(Collectors.toCollection(ArrayList::new))
                    : new ArrayList<>(Collections.singletonList(token)));
            System.out.print("Done. ");
        }

        if (args.length == 0) System.out.print("Enter user names or IDs: ");
        ArrayList<?> userNamesOrIds = getNamesOrIds(args.length != 0 ? args[0] : scanner.nextLine());

        if (args.length < 2) System.out.print("Enter place names or IDs: ");
        ArrayList<?> placeNamesOrIds = getNamesOrIds(args.length > 1 ? args[1] : scanner.nextLine());

        System.out.print("Fetching data.");
        searcher = new ForageRoblox(userNamesOrIds, placeNamesOrIds, true);
        System.out.println(" Searching...!");
        searcher.go();
    }

    public static ArrayList<?> getNamesOrIds(String output)
    {
        boolean alreadyIds = true;

        if (output.contains(","))
        {
            for (String part : output.split(","))
            {
                for (char coal : part.toCharArray())
                {
                    if (Character.isLetter(coal))
                        alreadyIds = false;
                }
            }

            return alreadyIds ? Arrays.stream(output.split(",")).map(Long::parseLong).collect(Collectors.toCollection(ArrayList::new))
                    : new ArrayList<>(Arrays.asList(output.split(",")));
        }

        else
        {
            for (char coal : output.toCharArray())
            {
                if (Character.isLetter(coal))
                    alreadyIds = false;
            }

            return alreadyIds ? new ArrayList<>(Collections.singletonList(Long.parseLong(output)))
                    : new ArrayList<>(Collections.singletonList(output));
        }
    }
}