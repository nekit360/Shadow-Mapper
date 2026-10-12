package nl.shadowlink.tools.shadowlib.model.wdr;

import nl.shadowlink.tools.io.ByteReader;
import nl.shadowlink.tools.shadowlib.model.collections.SimpleArray;
import nl.shadowlink.tools.shadowlib.utils.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Shadow-Link
 */
public class Shader {
    /** joaat("TextureSampler") - the name hash of the parameter that holds the diffuse map. */
    public static final int PARAM_TEXTURE_SAMPLER = 0x2B5170FD;

    /**
     * joaat-based hashes of the other parameters that carry a diffuse map.
     *
     * Not every shader names its map under "TextureSampler": `nj01_coast7b`, `nj01_18_ground2` and the
     * other eight models of NJ_01 that rendered white have the map under 0xD52B11DF, and the table in
     * `Utils.getShaderName` calls that one "DiffuseMap1" and 0x2420AFD1 "DiffuseMap2". They are tried in
     * this order, after "TextureSampler".
     */
    public static final int[] PARAM_DIFFUSE_MAPS = { PARAM_TEXTURE_SAMPLER, 0xD52B11DF, 0x2420AFD1 };

    private static final int TYPE_TEXTURE = 0;
    private static final int TYPE_VECTOR = 1;
    private static final int TYPE_MATRIX = 4;

    private int startOffset;

    private int VTable;
    private int BlockMapAdress;

    private int Unknown1;
    private byte Unknown2;
    private byte Unknown3;
    private int Unknown4;
    private int Unknown4_1;
    private int Unknown5;
    private int Unknown6;

    public int shaderParamOffsetsOffset;

    public int ShaderParamCount;
    private int Unknown8;

    public int shaderParamTypesOffset;

    public int shaderParamNameOffset;

    public int Hash;

    /**
     * Parameter names of this shader as `hash(type)`, for the log: shows which parameters a shader has
     * when it does not carry a diffuse map under "TextureSampler".
     */
    public String describeParams() {
        StringBuilder text = new StringBuilder();
        text.append("shader ").append(Utils.getHexString(Hash)).append(" params=").append(ShaderParamCount);
        int shown = Math.min(Math.min(ShaderParamCount, ShaderParamNames.Count), 32);
        for (int i = 0; i < shown; i++) {
            try {
                text.append(' ').append(Utils.getHexString(ShaderParamNames.Values.get(i))).append('(')
                    .append(i < ShaderParamTypes.Values.size() ? ShaderParamTypes.Values.get(i) : -1).append(')');
            } catch (RuntimeException e) {
                // a shader whose parameter list does not read is described as far as it goes
                break;
            }
        }
        return text.toString();
    }
    private int Unknown9;
    private int Unknown10;
    private int Unknown11;
    private int Unknown12;
    private int Unknown13;

    public SimpleArray<Integer> ShaderParamOffsets;
    public SimpleArray<Byte> ShaderParamTypes;
    public SimpleArray<Integer> ShaderParamNames;

    /** One entry per shader parameter, index aligned with ShaderParamNames/Offsets/Types; null when the type is unknown. */
    public ArrayList ShaderParams = new ArrayList();

    /* public Dictionary<ParamNameHash, IShaderParam> ShaderParams { get; private set; } public T
     * GetInfoData<T>(ParamNameHash hash) where T : class, IShaderParam { IShaderParam value;
     * ShaderParams.TryGetValue(hash, out value); return value as T; } */

    public Shader(ByteReader br) {
        startOffset = br.getCurrentOffset();

        VTable = br.readUInt32();
        BlockMapAdress = br.readOffset();

        Unknown1 = br.readUInt16();
        Unknown2 = br.readByte();
        Unknown3 = br.readByte();

        Unknown4 = br.readUInt16();
        Unknown4_1 = br.readUInt16();

        Unknown5 = br.readUInt32();

        shaderParamOffsetsOffset = br.readOffset();

        Unknown6 = br.readUInt32();
        ShaderParamCount = br.readUInt32();
        Unknown8 = br.readUInt32();

        shaderParamTypesOffset = br.readOffset();

        Hash = br.readUInt32();
        Unknown9 = br.readUInt32();
        Unknown10 = br.readUInt32();

        shaderParamNameOffset = br.readOffset();

        Unknown11 = br.readUInt32();
        Unknown12 = br.readUInt32();
        Unknown13 = br.readUInt32();

        int save = br.getCurrentOffset();

        br.setCurrentOffset(shaderParamOffsetsOffset);
        ShaderParamOffsets = new SimpleArray<Integer>(br, ShaderParamCount, 1);

        br.setCurrentOffset(shaderParamTypesOffset);
        ShaderParamTypes = new SimpleArray<Byte>(br, ShaderParamCount, 2);

        br.setCurrentOffset(shaderParamNameOffset);
        ShaderParamNames = new SimpleArray<Integer>(br, ShaderParamCount, 0);

        for (int i = 0; i < ShaderParamCount; i++) {
            if (ShaderParamOffsets.Values.get(i) != -1) {
                br.setCurrentOffset(ShaderParamOffsets.Values.get(i));
                switch (ShaderParamTypes.Values.get(i)) {
                    case TYPE_TEXTURE:
                        ShaderParamTexture test = new ShaderParamTexture();
                        // the texture name only exists after read(), so it has to be read here: without
                        // it every shader of every model ended up with an empty name and no texture
                        try {
                            test.read(br);
                        } catch (RuntimeException e) {
                            // a parameter that does not parse must not take the whole model down
                            test.TextureName = "";
                        }
                        ShaderParams.add(test);
                        break;
                    case TYPE_VECTOR:
                        ShaderParamVector test1 = new ShaderParamVector();
                        //test1.read(br);
                        ShaderParams.add(test1);
                        break;
                    case TYPE_MATRIX:
                        ShaderParamMatrix test2 = new ShaderParamMatrix();
                        // test2.read(br);
                        ShaderParams.add(test2);
                        break;
                    default:
                        // keep the list index aligned with ShaderParamNames/Offsets/Types
                        ShaderParams.add(null);
                }
            } else {
                // Message.displayMsgLow("WTF Der zit een shader @ -1 " + Utils.getHexString(startOffset));
                ShaderParams.add(null);
            }
        }

        // not sure what to do here

        /* ShaderParams = new Dictionary<ParamNameHash, IShaderParam>(ShaderParamCount); for (int i = 0; i <
         * ShaderParamCount; i++) { try { var obj = ParamObjectFactory.Create((ParamType) ShaderParamTypes[i]);
         * br.BaseStream.Seek(ShaderParamOffsets[i], SeekOrigin.Begin); obj.read(br); ShaderParams.Add((ParamNameHash)
         * ShaderParamNames[i], obj); } catch { ShaderParams.Add((ParamNameHash) ShaderParamNames[i], null); } } */
        br.setCurrentOffset(save);
    }

    /**
     * The diffuse map of this shader.
     *
     * @return the parameter, or null when this shader has none (or it did not parse)
     */
    public ShaderParamTexture getTextureParam() {
        for (int map = 0; map < PARAM_DIFFUSE_MAPS.length; map++) {
            for (int i = 0; i < ShaderParamCount && i < ShaderParamNames.Count; i++) {
                if (ShaderParamNames.Values.get(i) != PARAM_DIFFUSE_MAPS[map]) continue;
                if (i < ShaderParams.size() && ShaderParams.get(i) instanceof ShaderParamTexture) {
                    return (ShaderParamTexture) ShaderParams.get(i);
                }
            }
        }
        return null;
    }

    /**
     * The names of every diffuse map this shader carries, the one under "TextureSampler" first.
     *
     * A shader can hold more than one (a model's two shaders both name `nj01_coast7b_texture`, one under
     * "TextureSampler" and one under 0xD52B11DF), and a name may live in only one of the dictionaries a
     * model has, so the renderer tries them in order instead of settling for the first.
     *
     * @return the names, without duplicates and without empty ones
     */
    public List<String> getTextureNames() {
        List<String> names = new ArrayList<String>();
        for (int map = 0; map < PARAM_DIFFUSE_MAPS.length; map++) {
            for (int i = 0; i < ShaderParamCount && i < ShaderParamNames.Count; i++) {
                if (ShaderParamNames.Values.get(i) != PARAM_DIFFUSE_MAPS[map]) continue;
                if (i >= ShaderParams.size() || !(ShaderParams.get(i) instanceof ShaderParamTexture)) continue;
                String name = ((ShaderParamTexture) ShaderParams.get(i)).TextureName;
                if (name != null && !name.isEmpty() && !names.contains(name)) {
                    names.add(name);
                }
            }
        }
        return names;
    }

    public String[] getDataNames() {
        ArrayList<String> nameList = new ArrayList();

        nameList.add("VTable");
        nameList.add("BlockMapAdress");
        nameList.add("Unknown1");
        nameList.add("Unknown2");
        nameList.add("Unknown3");
        nameList.add("Unknown4");
        nameList.add("Unknown4_1");
        nameList.add("Unknown5");
        nameList.add("shaderParamOffsetsOffset");
        nameList.add("Unknown6");
        nameList.add("ShaderParamCount");
        nameList.add("Unknown8");
        nameList.add("shaderParamTypesOffset");
        nameList.add("Hash");
        nameList.add("Unknown9");
        nameList.add("Unknown10");
        nameList.add("shaderParamNameOffset");
        nameList.add("Unknown11");
        nameList.add("Unknown12");
        nameList.add("Unknown13");

        nameList.add("[ShaderParamOffsets]");
        for (int i = 0; i < ShaderParamOffsets.Count; i++) {
            nameList.add("  ShaderParamOffset " + i);
        }

        nameList.add("[ShaderParamTypes]");
        for (int i = 0; i < ShaderParamTypes.Count; i++) {
            nameList.add("  ShaderParamType " + i);
        }

        nameList.add("[ShaderParamNames]");
        for (int i = 0; i < ShaderParamNames.Count; i++) {
            nameList.add("  ShaderParamName " + i);
        }

        String[] names = new String[nameList.size()];
        for (int i = 0; i < nameList.size(); i++) {
            names[i] = nameList.get(i);
        }

        return names;
    }

    public String[] getDataValues() {
        ArrayList valueList = new ArrayList();

        valueList.add("" + VTable);
        valueList.add("" + BlockMapAdress);
        valueList.add("" + Unknown1);
        valueList.add("" + Unknown2);
        valueList.add("" + Unknown3);
        valueList.add("" + Unknown4);
        valueList.add("" + Unknown4_1);
        valueList.add("" + Unknown5);
        valueList.add("" + Utils.getHexString(shaderParamOffsetsOffset));
        valueList.add("" + Unknown6);
        valueList.add("" + ShaderParamCount);
        valueList.add("" + Unknown8);
        valueList.add("" + Utils.getHexString(shaderParamTypesOffset));
        valueList.add("" + Hash);
        valueList.add("" + Unknown9);
        valueList.add("" + Unknown10);
        valueList.add("" + Utils.getHexString(shaderParamNameOffset));
        valueList.add("" + Unknown11);
        valueList.add("" + Unknown12);
        valueList.add("" + Unknown13);

        valueList.add("" + ShaderParamOffsets.Count);
        for (int i = 0; i < ShaderParamOffsets.Count; i++) {
            valueList.add("" + Utils.getHexString(ShaderParamOffsets.Values.get(i)));
        }

        valueList.add("" + ShaderParamTypes.Count);
        for (int i = 0; i < ShaderParamTypes.Count; i++) {
            valueList.add(Utils.getShaderType(ShaderParamTypes.Values.get(i)));
        }

        valueList.add("" + ShaderParamNames.Count);
        for (int i = 0; i < ShaderParamNames.Count; i++) {
            valueList.add(Utils.getShaderName(ShaderParamNames.Values.get(i)));
        }

        String[] values = new String[valueList.size()];

        for (int i = 0; i < valueList.size(); i++) {
            values[i] = (String) valueList.get(i);
        }

        return values;
    }
}
