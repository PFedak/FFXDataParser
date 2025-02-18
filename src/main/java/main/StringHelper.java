package main;

import reading.ChunkedFileHelper;
import reading.FileAccessorWithMods;

import java.io.File;
import java.util.*;

public abstract class StringHelper {
    public static final String ANSI_RESET = "\u001B[0m";
    public static final String ANSI_BLACK = "\u001B[30m";
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_GREEN = "\u001B[32m";
    public static final String ANSI_YELLOW = "\u001B[33m";
    public static final String ANSI_BLUE = "\u001B[34m";
    public static final String ANSI_PURPLE = "\u001B[35m";
    public static final String ANSI_CYAN = "\u001B[36m";
    public static final String ANSI_WHITE = "\u001B[37m";

    public static final boolean COLORS_USE_CONSOLE_CODES = true;
    public static final Map<Integer, Character> BIN_LOOKUP = new HashMap<>();
    public static final Map<Character, Integer> BIN_REV_LOOKUP = new HashMap<>();

    public static Integer charToByte(char chr) {
        return BIN_REV_LOOKUP.get(chr);
    }

    public static Integer charToByte(int chr) {
        return charToByte((char) chr);
    }

    public static Character byteToChar(int hex) {
        return BIN_LOOKUP.get(hex);
    }

    public static String getColorString(int hex) {
        if (COLORS_USE_CONSOLE_CODES) {
            return byteToConsoleColor(hex);
        } else {
            return "{CLR:" + byteToColor(hex) + '}';
        }
    }

    public static String byteToColor(int hex) {
        return switch (hex) {
            case 0x41 -> "WHITE";
            case 0x43 -> "YELLOW";
            case 0x52 -> "GREY";
            case 0x88 -> "BLUE";
            case 0x94 -> "RED";
            case 0x97 -> "PINK";
            case 0xA1 -> "OL_PURPLE";
            case 0xB1 -> "OL_CYAN";
            default -> String.format("%02X", hex);
        };
    }

    public static String byteToConsoleColor(int hex) {
        return switch (hex) {
            case 0x41 -> ANSI_RESET;
            case 0x43 -> ANSI_YELLOW;
            case 0x52 -> ANSI_BLACK;
            case 0x88 -> ANSI_BLUE;
            case 0x94 -> ANSI_RED;
            case 0xA1 -> ANSI_PURPLE;
            case 0xB1 -> ANSI_CYAN;
            default -> "";
        };
    }

    public static String consoleColorIfEnabled(String ansiColor) {
        return COLORS_USE_CONSOLE_CODES ? ansiColor : "";
    }

    public static List<String> readStringFile(String filename, boolean print) {
        File file = FileAccessorWithMods.getRealFile(filename);
        if (file.isDirectory()) {
            String[] contents = file.list();
            if (contents != null) {
                Arrays.stream(contents).filter(sf -> !sf.startsWith(".")).sorted().forEach(sf -> readStringFile(filename + '/' + sf, print));
            }
            return null;
        }
        int[] bytes = ChunkedFileHelper.fileToBytes(FileAccessorWithMods.resolveFile(filename, print));
        return readStringData(bytes, print);
    }

    public static List<String> readStringData(int[] bytes, boolean print) {
        if (bytes == null || bytes.length <= 0) {
            return null;
        }
        int first = bytes[0x00] + bytes[0x01] * 0x100;
        int second = bytes[0x04] + bytes[0x05] * 0x100;
        boolean clones = first == second;
        int count = first / (clones ? 0x08 : 0x04);
        List<String> strings = new ArrayList<>(count);
        try {
            for (int i = 0; i < count; i++) {
                int addr = i * (clones ? 0x08 : 0x04);
                int offset = bytes[addr] + bytes[addr + 0x01] * 0x100;
                int somethingElse = bytes[addr + 0x02];
                int options = bytes[addr + 0x03];
                if (print) {
                    String choosable = options > 0 ? " (" + options + " selectable)" : "";
                    System.out.print("String #" + i + " [" + String.format("%04X", offset) + "h]" + choosable + ":");
                }
                String out = getStringAtLookupOffset(bytes, offset);
                if (print) {
                    System.out.println(out);
                }
                strings.add(out);
                if (clones) {
                    int clonedOffset = bytes[addr + 0x04] + bytes[addr + 0x05] * 0x100;
                    int clonedSomethingElse = bytes[addr + 0x06];
                    int clonedChoosableOptions = bytes[addr + 0x07];
                    if (offset != clonedOffset) {
                        System.err.println("offset " + i + " not cloned: offset " + String.format("%04X", offset) + "; other " + String.format("%04X", clonedOffset));
                    } else if (options != clonedChoosableOptions) {
                        System.err.println("options " + i + " not cloned: original " + options + "; other " + clonedChoosableOptions);
                    } else if (somethingElse != clonedSomethingElse) {
                        System.err.println("somethingElse " + i + " not cloned: original " + somethingElse + "; other " + clonedSomethingElse);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Exception during string data reading. (" + e.getLocalizedMessage() + ")");
        }
        return strings;
    }

    public static String getStringAtLookupOffset(int[] table, int offset) {
        if (offset >= table.length) {
            return "{OOB}";
        }
        StringBuilder out = new StringBuilder();
        int idx = table[offset];
        boolean anyColorization = false;
        while (idx != 0x00) {
            Character chr = byteToChar(idx);
            if (chr != null) {
                out.append(chr);
            } else if (idx == 0x0A) {
                offset++;
                int clr = table[offset];
                out.append(getColorString(clr));
                anyColorization = true;
            } else if (idx >= 0x13 && idx <= 0x22) {
                int section = idx - 0x13;
                offset++;
                int line = table[offset];
                out.append("{MCR:s").append(String.format("%02X", section)).append('l').append(String.format("%02X", line)).append('}');
            } else {
                out.append('?');
            }
            offset++;
            if (offset >= table.length) {
                return out.append("{OOB}").toString();
            }
            idx = table[offset];
        }
        if (COLORS_USE_CONSOLE_CODES && anyColorization) {
            out.append(ANSI_RESET);
        }
        return out.toString();
    }

    public static void initialize() {
        BIN_LOOKUP.put(0x00, '\n');
        BIN_LOOKUP.put(0x03, '\n');
        BIN_LOOKUP.put(0x30, '0');
        BIN_LOOKUP.put(0x31, '1');
        BIN_LOOKUP.put(0x32, '2');
        BIN_LOOKUP.put(0x33, '3');
        BIN_LOOKUP.put(0x34, '4');
        BIN_LOOKUP.put(0x35, '5');
        BIN_LOOKUP.put(0x36, '6');
        BIN_LOOKUP.put(0x37, '7');
        BIN_LOOKUP.put(0x38, '8');
        BIN_LOOKUP.put(0x39, '9');
        BIN_LOOKUP.put(0x3a, ' ');
        BIN_LOOKUP.put(0x3b, '!');
        BIN_LOOKUP.put(0x3c, '\"');
        BIN_LOOKUP.put(0x3d, '#');
        BIN_LOOKUP.put(0x3e, '$');
        BIN_LOOKUP.put(0x3f, '%');
        BIN_LOOKUP.put(0x40, '&');
        BIN_LOOKUP.put(0x41, '\'');
        BIN_LOOKUP.put(0x42, '(');
        BIN_LOOKUP.put(0x43, ')');
        BIN_LOOKUP.put(0x44, '*');
        BIN_LOOKUP.put(0x45, '+');
        BIN_LOOKUP.put(0x46, ',');
        BIN_LOOKUP.put(0x47, '-');
        BIN_LOOKUP.put(0x48, '.');
        BIN_LOOKUP.put(0x49, '/');
        BIN_LOOKUP.put(0x4a, ':');
        BIN_LOOKUP.put(0x4b, ';');
        BIN_LOOKUP.put(0x4c, '<');
        BIN_LOOKUP.put(0x4d, '=');
        BIN_LOOKUP.put(0x4e, '>');
        BIN_LOOKUP.put(0x4f, '?');

        BIN_LOOKUP.put(0x50, 'A');
        BIN_LOOKUP.put(0x51, 'B');
        BIN_LOOKUP.put(0x52, 'C');
        BIN_LOOKUP.put(0x53, 'D');
        BIN_LOOKUP.put(0x54, 'E');
        BIN_LOOKUP.put(0x55, 'F');
        BIN_LOOKUP.put(0x56, 'G');
        BIN_LOOKUP.put(0x57, 'H');
        BIN_LOOKUP.put(0x58, 'I');
        BIN_LOOKUP.put(0x59, 'J');
        BIN_LOOKUP.put(0x5A, 'K');
        BIN_LOOKUP.put(0x5B, 'L');
        BIN_LOOKUP.put(0x5C, 'M');
        BIN_LOOKUP.put(0x5D, 'N');
        BIN_LOOKUP.put(0x5E, 'O');
        BIN_LOOKUP.put(0x5F, 'P');
        BIN_LOOKUP.put(0x60, 'Q');
        BIN_LOOKUP.put(0x61, 'R');
        BIN_LOOKUP.put(0x62, 'S');
        BIN_LOOKUP.put(0x63, 'T');
        BIN_LOOKUP.put(0x64, 'U');
        BIN_LOOKUP.put(0x65, 'V');
        BIN_LOOKUP.put(0x66, 'W');
        BIN_LOOKUP.put(0x67, 'X');
        BIN_LOOKUP.put(0x68, 'Y');
        BIN_LOOKUP.put(0x69, 'Z');
        BIN_LOOKUP.put(0x6a, '[');
        BIN_LOOKUP.put(0x6b, '\\');
        BIN_LOOKUP.put(0x6c, ']');
        BIN_LOOKUP.put(0x6d, '\u0361');      //  ͡ , could maybe be replaced with '^'? «https://en.wikipedia.org/wiki/Inverted_breve»
        BIN_LOOKUP.put(0x6e, '＿');                 // TODO: Replace with a normal underscore if no other (shorter) such is found in the game font
        BIN_LOOKUP.put(0x6f, '`');
        BIN_LOOKUP.put(0x70, 'a');
        BIN_LOOKUP.put(0x71, 'b');
        BIN_LOOKUP.put(0x72, 'c');
        BIN_LOOKUP.put(0x73, 'd');
        BIN_LOOKUP.put(0x74, 'e');
        BIN_LOOKUP.put(0x75, 'f');
        BIN_LOOKUP.put(0x76, 'g');
        BIN_LOOKUP.put(0x77, 'h');
        BIN_LOOKUP.put(0x78, 'i');
        BIN_LOOKUP.put(0x79, 'j');
        BIN_LOOKUP.put(0x7A, 'k');
        BIN_LOOKUP.put(0x7B, 'l');
        BIN_LOOKUP.put(0x7C, 'm');
        BIN_LOOKUP.put(0x7D, 'n');
        BIN_LOOKUP.put(0x7E, 'o');
        BIN_LOOKUP.put(0x7F, 'p');
        BIN_LOOKUP.put(0x80, 'q');
        BIN_LOOKUP.put(0x81, 'r');
        BIN_LOOKUP.put(0x82, 's');
        BIN_LOOKUP.put(0x83, 't');
        BIN_LOOKUP.put(0x84, 'u');
        BIN_LOOKUP.put(0x85, 'v');
        BIN_LOOKUP.put(0x86, 'w');
        BIN_LOOKUP.put(0x87, 'x');
        BIN_LOOKUP.put(0x88, 'y');
        BIN_LOOKUP.put(0x89, 'z');

        BIN_LOOKUP.put(0x8a, '{');
        BIN_LOOKUP.put(0x8b, '|');
        BIN_LOOKUP.put(0x8c, '}');
        BIN_LOOKUP.put(0x8d, '～');                 // Fullwidth tilde
        BIN_LOOKUP.put(0x8e, '•');
        BIN_LOOKUP.put(0x8f, '【');
        BIN_LOOKUP.put(0x90, '】');
        BIN_LOOKUP.put(0x91, '♪');
        BIN_LOOKUP.put(0x92, '♥');

        BIN_LOOKUP.put(0x94, '“');
        BIN_LOOKUP.put(0x95, '”');
        BIN_LOOKUP.put(0x96, '—');

        BIN_LOOKUP.put(0x98, '¡');
        BIN_LOOKUP.put(0x99, '↑');
        BIN_LOOKUP.put(0x9a, '↓');
        BIN_LOOKUP.put(0x9b, '←');
        BIN_LOOKUP.put(0x9c, '→');
        BIN_LOOKUP.put(0x9d, '̈');                 // TODO: Consider replacing with '¨' if this isn't represented by some other character code
        BIN_LOOKUP.put(0x9e, '«');
        BIN_LOOKUP.put(0x9f, '°');

        BIN_LOOKUP.put(0xa1, '»');
        BIN_LOOKUP.put(0xa2, '¿');
        BIN_LOOKUP.put(0xa3, 'À');
        BIN_LOOKUP.put(0xa4, 'Á');
        BIN_LOOKUP.put(0xa5, 'Â');
        BIN_LOOKUP.put(0xa6, 'Ä');
        BIN_LOOKUP.put(0xa7, 'Ç');
        BIN_LOOKUP.put(0xa8, 'È');
        BIN_LOOKUP.put(0xa9, 'É');
        BIN_LOOKUP.put(0xaa, 'Ê');
        BIN_LOOKUP.put(0xab, 'Ë');
        BIN_LOOKUP.put(0xac, 'Ì');
        BIN_LOOKUP.put(0xad, 'Í');
        BIN_LOOKUP.put(0xae, 'Î');
        BIN_LOOKUP.put(0xaf, 'Ï');
        BIN_LOOKUP.put(0xb0, 'Ñ');
        BIN_LOOKUP.put(0xb1, 'Ò');
        BIN_LOOKUP.put(0xb2, 'Ó');
        BIN_LOOKUP.put(0xb3, 'Ô');
        BIN_LOOKUP.put(0xb4, 'Ö');
        BIN_LOOKUP.put(0xb5, 'Ù');
        BIN_LOOKUP.put(0xb6, 'Ú');
        BIN_LOOKUP.put(0xb7, 'Û');
        BIN_LOOKUP.put(0xb8, 'Ü');
        BIN_LOOKUP.put(0xb9, 'ß');
        BIN_LOOKUP.put(0xba, 'à');
        BIN_LOOKUP.put(0xbb, 'á');
        BIN_LOOKUP.put(0xbc, 'â');
        BIN_LOOKUP.put(0xbd, 'ä');
        BIN_LOOKUP.put(0xbe, 'ç');
        BIN_LOOKUP.put(0xbf, 'è');
        BIN_LOOKUP.put(0xc0, 'é');
        BIN_LOOKUP.put(0xc1, 'ê');
        BIN_LOOKUP.put(0xc2, 'ë');
        BIN_LOOKUP.put(0xc3, 'ì');
        BIN_LOOKUP.put(0xc4, 'í');
        BIN_LOOKUP.put(0xc5, 'î');
        BIN_LOOKUP.put(0xc6, 'ï');
        BIN_LOOKUP.put(0xc7, 'ñ');
        BIN_LOOKUP.put(0xc8, 'ò');
        BIN_LOOKUP.put(0xc9, 'ó');
        BIN_LOOKUP.put(0xca, 'ô');
        BIN_LOOKUP.put(0xcb, 'ö');
        BIN_LOOKUP.put(0xcc, 'ù');
        BIN_LOOKUP.put(0xcd, 'ú');
        BIN_LOOKUP.put(0xce, 'û');
        BIN_LOOKUP.put(0xcf, 'ü');
        BIN_LOOKUP.put(0xd0, '，');   // This appears to be another comma, positioned about one pixel higher vertically than the 0x46 one. We'll use a fullwidth one here.
        BIN_LOOKUP.put(0xd1, 'ƒ');   // Not sure whether this is supposed to be a musical forte character or a mathematical function "f"; the former can't be represented
        BIN_LOOKUP.put(0xd2, '„');
        BIN_LOOKUP.put(0xd3, '…');
        BIN_LOOKUP.put(0xd4, '‘');
        BIN_LOOKUP.put(0xd5, '’');
        BIN_LOOKUP.put(0xd6, '▪');   // Not really what the in-game symbol looks like but it'll do I guess
        BIN_LOOKUP.put(0xd7, '–');   // Shorter dash than 0x96, but not a hyphen?
        BIN_LOOKUP.put(0xd8, '~');   // "Normal" tilde, as opposed to the fullwidth one at 0x8d
        BIN_LOOKUP.put(0xd9, '™');

// NOTE: 0x93, 0x97, 0xa0 and 0xda seem to be unused and will print nothing if used in game text

        BIN_LOOKUP.put(0xdb, '›');
        BIN_LOOKUP.put(0xdc, '§');
        BIN_LOOKUP.put(0xdd, '©');
        BIN_LOOKUP.put(0xde, 'ₐ');   // This is actually a superscripted 'a' in the game's font, but this is the closest there seems to be in the 16-bit unicode charset
        BIN_LOOKUP.put(0xdf, '®');
        BIN_LOOKUP.put(0xe0, '±');
        BIN_LOOKUP.put(0xe1, '²');
        BIN_LOOKUP.put(0xe2, '³');
        BIN_LOOKUP.put(0xe3, '¼');
        BIN_LOOKUP.put(0xe4, '½');
        BIN_LOOKUP.put(0xe5, '¾');
        BIN_LOOKUP.put(0xe6, '×');
        BIN_LOOKUP.put(0xe7, '÷');
        BIN_LOOKUP.put(0xe8, '‹');
        BIN_LOOKUP.put(0xe9, '⋯');   // Midline horizontal ellipsis as opposed to the baseline version at character 0xd3

// The characters in-between do not print anything and are presumably either not valid characters or act as
// placeholders for character names and the like in certain contexts.

        BIN_LOOKUP.put(0xfc, '\t');  // Wide space or tab, not sure which; all the remaining characters up to 0xff appears this way when typed so probably not valid

        BIN_LOOKUP.forEach((i, c) -> BIN_REV_LOOKUP.put(c, i));
    }
}
