package nl.shadowlink.tools.shadowlib.model.wdr;

import nl.shadowlink.tools.io.ByteReader;

/**
 * @author Shadow-Link
 */
public class ResourceFile {
    private ResourceHeader _header;
    private Compression _codec;

    public ResourceFile() {
    }

    public byte[] read(ByteReader br, int fileSize) {
        _header = new ResourceHeader();

        _header.read(br);

        if (_header.Magic != ResourceHeader.MagicValue) {
            throw new IllegalStateException("Invalid resource file");
        }

        _codec = new Compression();

        switch (_header.CompressCodec) {
            case LZX:
                // Message.displayMsgHigh("LZX");
                _codec.setCodec(_header.CompressCodec);
                break;
            case Deflate:
                // Message.displayMsgHigh("Deflate");
                _codec.setCodec(_header.CompressCodec);
                break;
            default:
                // Message.displayMsgHigh("Compressie fail");
        }

        int totalMemSize = _header.getSystemMemSize() + _header.getGraphicsMemSize();
        // Message.displayMsgHigh("Total Mem Size: " + totalMemSize);
        // Message.displayMsgHigh("Max buffer size: " + br.moreToRead());
        // Message.displayMsgHigh("File Size: " + fileSize);
        if (fileSize == -1)
            fileSize = br.moreToRead();

        // readRest() - toArray wants an *absolute* end offset, passing moreToRead() (a length) as
        // the end cut the last bytes off the compressed slice. Small resources lost enough of the
        // stream that it never finished: the dictionary came out as zeros and the first pointer
        // read threw "Expected an offset" (12 of 107 .wtd in nj_01.img, 32 of 101 in nj_02.img).
        byte stream[] = _codec.decompress(br.readRest(), totalMemSize);
        if (!_codec.isLastComplete()) {
            System.out.println("Resource stream incomplete: inflated " + _codec.getLastInflated()
                + " of " + totalMemSize + " bytes");
        }

        // Message.displayMsgHigh("System Mem Size: " + _header.GetSystemMemSize());
        // Message.displayMsgHigh("Graphic Mem Size: " + _header.GetGraphicsMemSize());

        return stream;
    }

    public int getGraphicSize() {
        return _header.getGraphicsMemSize();
    }

    public int getSystemSize() {
        return _header.getSystemMemSize();
    }

    /* public void Write(byte stream[], String file){ FileOutputStream fs = null; try { fs = new FileOutputStream(file);
     * DataOutputStream out = new DataOutputStream(fs); _header.Write(out); _codec.compress(out, stream); } catch
     * (FileNotFoundException ex) { Logger.getLogger(ResourceFile.class.getName()).log(Level.SEVERE, null, ex); }
     * finally { try { fs.close(); } catch (IOException ex) {
     * Logger.getLogger(ResourceFile.class.getName()).log(Level.SEVERE, null, ex); } } } */
}
