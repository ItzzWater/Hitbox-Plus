package itzglecni.customhitboxes.config;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

// Rules for the Team Color player list, shared by the config screen and /hbteam.
public final class TeamNames {
    // Java names are 3-16 of A-Z, 0-9 and _. Allow a little more (1-20, and ".") for
    // offline-mode and Bedrock (Geyser/Floodgate) players, whose names often start with ".".
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_.]{1,20}");

    private TeamNames() {
    }

    public static boolean isValid(String name) {
        return name != null && VALID.matcher(name).matches();
    }

    public static boolean contains(List<String> names, String name) {
        return indexOf(names, name) >= 0;
    }

    // Adds the name if it is valid and not already in the list. Returns true if it was added.
    public static boolean add(List<String> names, String name) {
        if (name == null) {
            return false;
        }
        String trimmed = name.trim();
        if (!isValid(trimmed) || contains(names, trimmed)) {
            return false;
        }
        names.add(trimmed);
        return true;
    }

    // Removes the name (any letter case). Returns the stored spelling, or null if it was not in the list.
    public static String remove(List<String> names, String name) {
        int index = indexOf(names, name);
        return index >= 0 ? names.remove(index) : null;
    }

    // "Steve, Alex,,  Notch" -> [Steve, Alex, Notch]
    public static List<String> split(String input) {
        List<String> out = new ArrayList<>();
        if (input == null) {
            return out;
        }
        for (String part : input.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }

    public static Set<String> lowerCaseSet(List<String> names) {
        Set<String> out = new HashSet<>();
        if (names != null) {
            for (String name : names) {
                out.add(name.toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    private static int indexOf(List<String> names, String name) {
        if (names == null || name == null) {
            return -1;
        }
        String trimmed = name.trim();
        for (int i = 0; i < names.size(); i++) {
            if (names.get(i).equalsIgnoreCase(trimmed)) {
                return i;
            }
        }
        return -1;
    }
}
