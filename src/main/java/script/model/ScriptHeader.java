package script.model;

import script.ScriptObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ScriptHeader {
    public int scriptType;
    public int variablesCount;
    public int refIntCount;
    public int refFloatCount;
    public int entryPointCount;
    public int jumpCount;
    public int alwaysZero1;
    public int privateDataLength;
    public int variableStructsTableOffset;
    public int intTableOffset;
    public int floatTableOffset;
    public int scriptEntryPointsOffset;
    public int jumpsOffset;
    public int alwaysZero2;
    public int privateDataOffset;
    public int sharedDataOffset;

    public ScriptJump[] entryPoints;
    public ScriptJump[] jumps;
    public ScriptVariable[] variableDeclarations;
    public int[] refFloats;
    public int[] refInts;
    public List<ScriptVariable> privateVars;
    public List<ScriptVariable> sharedVars;

    public String getNonCommonString() {
        List<String> list = new ArrayList<>();
        list.add("Type=" + scriptTypeToString(scriptType) + " [" + scriptType + "h]");
        list.add("Entrypoints=" + entryPointCount);
        list.add("Jumps=" + jumpCount);
        list.add(privateValuesString());
        /* list.add(privateDataOffset != 0 ? "privateDataOffset=" + String.format("%04X", privateDataOffset) : "");
        list.add(privateDataLength != 0 ? "privateDataLength=" + String.format("%04X", privateDataLength) : ""); */
        list.add(alwaysZero1 != 0 ? "alwaysZero1=" + alwaysZero1 : "");
        list.add(alwaysZero2 != 0 ? "alwaysZero2=" + alwaysZero2 : "");
        String full = list.stream().filter(s -> s != null && !s.isBlank()).collect(Collectors.joining(", "));
        return "{ " + full + " }";
    }

    public void setVariableInitialValues(ScriptObject script, int[] bytes) {
        if (variableDeclarations == null || variableDeclarations.length == 0) {
            return;
        }
        privateVars = new ArrayList<>();
        sharedVars = new ArrayList<>();
        final int maxPrivate = privateDataOffset + privateDataLength;
        for (ScriptVariable vr : variableDeclarations) {
            if (vr.location == 3) {
                int max = privateDataOffset + vr.offset + vr.getLength();
                if (max <= maxPrivate) {
                    privateVars.add(new ScriptVariable(vr));
                }
            } else if (vr.location == 4) {
                sharedVars.add(new ScriptVariable(vr));
            }
        }
        privateVars.forEach(p -> p.parseValues(script, bytes, privateDataOffset));
        sharedVars.forEach(s -> s.parseValues(script, bytes, sharedDataOffset));
    }

    private static String formatUnknownByte(int bt) {
        return String.format("%02X", bt) + '=' + String.format("%03d", bt) + '(' + String.format("%8s", Integer.toBinaryString(bt)).replace(' ', '0') + ')';
    }

    public static String scriptTypeToString(int scriptType) {
        return switch (scriptType) {
            case 0 -> "Subroutine";
            case 1 -> "FieldObject";
            case 2 -> "BattleObject";
            case 4 -> "Cutscene";
            default -> "unknown?";
        };
    }

    private String privateValuesString() {
        if (privateVars == null || privateVars.isEmpty()) {
            return "";
        }
        String joined = privateVars.stream().filter(v -> !v.values.isEmpty()).map(v -> v.initString()).collect(Collectors.joining(", "));
        return "privateVars=[" + joined + "]";
    }

    private String sharedValuesString() {
        if (sharedVars == null || sharedVars.isEmpty()) {
            return "";
        }
        String joined = sharedVars.stream().filter(v -> !v.values.isEmpty()).map(ScriptVariable::valuesString).collect(Collectors.joining(", "));
        return "sharedVars=[" + joined + "]";
    }
}
