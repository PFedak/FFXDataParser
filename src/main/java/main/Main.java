package main;

import reading.FileAccessorWithMods;
import script.MonsterFile;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static main.DataReadingManager.*;

public class Main {

    private static final String MODE_GREP = "GREP";
    private static final String MODE_TRANSLATE = "TRANSLATE";
    private static final String MODE_READ_ALL_ABILITIES = "READ_ALL_ABILITIES";
    private static final String MODE_READ_KEY_ITEMS = "READ_KEY_ITEMS";
    private static final String MODE_READ_GEAR_ABILITIES = "READ_GEAR_ABILITIES";
    private static final String MODE_READ_TREASURES = "READ_TREASURES";
    private static final String MODE_READ_GEAR_SHOPS = "READ_GEAR_SHOPS";
    private static final String MODE_READ_ITEM_SHOPS = "READ_ITEM_SHOPS";
    private static final String MODE_READ_MONSTER_LOCALIZATIONS = "READ_MONSTER_LOCALIZATIONS";
    private static final String MODE_READ_WEAPON_FILE = "READ_WEAPON_FILE";
    private static final String MODE_READ_STRING_FILE = "READ_STRING_FILE";
    private static final String MODE_PARSE_SCRIPT_FILE = "PARSE_SCRIPT_FILE";
    private static final String MODE_PARSE_ENCOUNTER = "PARSE_ENCOUNTER";
    private static final String MODE_PARSE_EVENT = "PARSE_EVENT";
    private static final String MODE_PARSE_MONSTER = "PARSE_MONSTER";
    private static final String MODE_READ_SPHERE_GRID_LAYOUT = "READ_SPHERE_GRID_LAYOUT";

    public static void main(String[] args) {
        String pathRoot = args[0];
        if (!".".equals(pathRoot)) {
            FileAccessorWithMods.GAME_FILES_ROOT = pathRoot;
        }
        String mode = args[1];
        List<String> realArgs = Arrays.asList(args).subList(2, args.length);
        initializeInternals();
        readAndPrepareDataModel();
        switch (mode) {
            case MODE_GREP:
                String joined = String.join(" ", realArgs);
                writeGrep(joined);
                for (String monstername : realArgs) {
                    writeGrep(monstername);
                }
                break;
            case MODE_TRANSLATE:
                String concat = String.join("", realArgs);
                translate(concat);
                break;
            case MODE_READ_ALL_ABILITIES:
                readAbilitiesFromFile(PATH_SKILL_TABLE_3, 3, true);
                readAbilitiesFromFile(PATH_SKILL_TABLE_4, 4, true);
                readAbilitiesFromFile(PATH_SKILL_TABLE_6, 6, true);
                readAbilitiesFromFile(PATH_SKILL_TABLE_2, 2, true);
                break;
            case MODE_PARSE_MONSTER:
                for (String arg : realArgs) {
                    int idx = Integer.parseInt(arg, 10);
                    int monsterIdx = idx + 0x1000;
                    MonsterFile monster = DataAccess.getMonster(monsterIdx);
                    if (monster != null) {
                        monster.parseScript();
                        System.out.println("Printing monster #" + arg + " [" + String.format("%04X", monsterIdx) + "h]");
                        System.out.println(monster);
                    } else {
                        System.err.println("Monster with idx " + arg + " not found");
                    }
                }
                break;
            case MODE_PARSE_ENCOUNTER:
                for (String filename : realArgs) {
                    readEncounterFull(filename, true);
                }
                break;
            case MODE_PARSE_EVENT:
                for (String filename : realArgs) {
                    readEventFull(filename, true);
                }
                break;
            case MODE_PARSE_SCRIPT_FILE:
                for (String filename : realArgs) {
                    if (filename.contains("battle/mon")) {
                        System.out.println("Monster file: " + filename);
                        readMonsterFile(filename, true);
                    } else if (filename.contains("battle/btl")) {
                        System.out.println("Encounter file: " + filename);
                        readEncounterFile(filename, true, null);
                    } else if (filename.contains("event/obj")) {
                        System.out.println("Event file: " + filename);
                        readEventFile(filename, true, null);
                    } else {
                        System.out.println("Failed to identify file: " + filename);
                        readEncounterFile(filename, true, null);
                    }
                }
                break;
            case MODE_READ_TREASURES:
                readTreasures(PATH_ORIGINALS_KERNEL + "takara.bin", true);
                break;
            case MODE_READ_GEAR_SHOPS:
                readWeaponShops(PATH_ORIGINALS_KERNEL + "arms_shop.bin", true);
                break;
            case MODE_READ_ITEM_SHOPS:
                readItemShops(PATH_ORIGINALS_KERNEL + "item_shop.bin", true);
                break;
            case MODE_READ_MONSTER_LOCALIZATIONS:
                readMonsterLocalizations(true);
                break;
            case MODE_READ_WEAPON_FILE:
                for (String filename : realArgs) {
                    readWeaponPickups(filename, true);
                }
                break;
            case MODE_READ_KEY_ITEMS:
                readKeyItemsFromFile(PATH_LOCALIZED_KERNEL + "important.bin", true);
                break;
            case MODE_READ_GEAR_ABILITIES:
                readGearAbilitiesFromFile(PATH_LOCALIZED_KERNEL + "a_ability.bin", true);
                break;
            case MODE_READ_SPHERE_GRID_LAYOUT:
                readSphereGridLayout(realArgs.get(0), realArgs.get(1), true);
                break;
            case MODE_READ_STRING_FILE:
                for (String filename : realArgs) {
                    StringHelper.readStringFile(filename, true);
                }
                break;
            default:
                break;
        }
    }

    private static void writeGrep(String str) {
        final StringBuilder search = new StringBuilder("grep -r \"");
        str.chars().map(StringHelper::charToByte).forEach(bc -> search.append("\\x").append(Integer.toHexString(bc)));
        final StringBuilder regular = new StringBuilder();
        str.chars().map(StringHelper::charToByte).forEach(bc -> regular.append(Integer.toHexString(bc)));
        search.append("\" .");
        System.out.println(str);
        System.out.println(regular);
        System.out.println(search);
        System.out.println("");
    }

    private static void translate(String str) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < str.length(); i += 2) {
            int idx = Integer.parseInt(str.substring(i, i+2), 16);
            Character chr = StringHelper.byteToChar(idx);
            if (chr != null) {
                out.append(chr);
            }
        }
        System.out.println(out);
    }

    private static void parseFileText(String filename) {
        System.out.println("--- " + filename + " ---");
        File file = FileAccessorWithMods.getRealFile(filename);
        if (file.isDirectory()) {
            String[] contents = file.list();
            if (contents != null) {
                Arrays.stream(contents).sorted().forEach(subfile -> parseFileText(filename + '/' + subfile));
            }
        } else {
            StringBuilder out = new StringBuilder();
            try {
                DataInputStream inputStream = FileAccessorWithMods.readFile(filename);
                while (inputStream.available() > 0) {
                    int idx = inputStream.readUnsignedByte();
                    Character chr = StringHelper.byteToChar(idx);
                    if (chr != null) {
                        out.append(chr);
                    }
                }
            } catch (IOException e) {}
            System.out.println(out.toString().trim());
        }
    }
}
