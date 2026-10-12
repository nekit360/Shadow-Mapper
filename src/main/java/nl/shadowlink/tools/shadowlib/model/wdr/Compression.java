

package nl.shadowlink.tools.shadowlib.model.wdr;


import java.io.DataOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;

/**
 *
 * @author Shadow-Link
 */
public class Compression {
    private CompressionType type;

    public void setCodec(CompressionType type){
        this.type = type;
    }

    public void compress(DataOutputStream destination, byte[] source){

        DeflaterOutputStream deflater = new DeflaterOutputStream(destination, new Deflater(Deflater.DEFAULT_COMPRESSION, true));

        for(int i = 0; i < source.length-1; i++){
            try {
                deflater.write(source[i]);
            } catch (IOException ex) {
                Logger.getLogger(Compression.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        try {
            deflater.finish();
        } catch (IOException ex) {
            Logger.getLogger(Compression.class.getName()).log(Level.SEVERE, null, ex);
        }

        //Message.displayMsgHigh("Deflate finished");
    }

    public byte[] decompress(byte[] source, int totalSize) {
        byte[] dataBuffer = new byte[totalSize+1];
        lastInflated = 0;
        lastComplete = false;
        try {
            Inflater inflater = new Inflater(true);
            inflater.setInput(source);
            // a single inflate() call returns as soon as it runs out of input or output, which is
            // not the same as the end of the stream: keep calling until it really finished
            while (!inflater.finished() && lastInflated < dataBuffer.length) {
                int produced = inflater.inflate(dataBuffer, lastInflated, dataBuffer.length - lastInflated);
                if (produced == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break;
                }
                lastInflated += produced;
            }
            lastComplete = inflater.finished();
            inflater.end();
        } catch (DataFormatException ex) {
            Logger.getLogger(Compression.class.getName()).log(Level.SEVERE, null, ex);
        }
        //writeToFile(dataBuffer);
        return dataBuffer;
    }

    private int lastInflated = 0;
    private boolean lastComplete = false;

    /** bytes the last decompress() produced */
    public int getLastInflated() {
        return lastInflated;
    }

    /** whether the last decompress() saw the end of the compressed stream */
    public boolean isLastComplete() {
        return lastComplete;
    }

    /*public void writeToFile(byte[] source){
        WriteFunctions wf = new WriteFunctions();
        FileOutputStream file_out = wf.openFile("wft2.dec");
        DataOutputStream data_out = new DataOutputStream(file_out);
        for(int i = 0; i < source.length-1; i++){
            wf.writeByte(data_out, source[i]);
        }
        wf.closeFile(file_out, data_out);
    }*/
}
