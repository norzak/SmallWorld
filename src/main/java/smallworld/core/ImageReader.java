package smallworld.core;

import java.io.InputStream;
import java.io.IOException;
import java.io.InputStream;

class ImageReader {
  private final InputStream in;
  private int numSmallInts;
  private SmallObject[] objectPool;

  public ImageReader(InputStream in) {
    this.in = in;
    this.objectPool = null;
  }
  
  //Prepare for reading Image file from a byte array instead of stream
  private int readInt(InputStream in) {
      return ((in.read() & 0xFF) << 24) | ((in.read() & 0xFF) << 16)
              | ((in.read() & 0xFF) << 8) | (in.read() & 0xFF);
  }

  public SmallObject readObject() throws IOException {
    if (objectPool == null) {
      readObjects();
    }
    // InputStream should now point to the index of a root
    return objectPool[readInt(in)];
  }

  private void readObjects() throws IOException {
    if (readInt(in) != 0x53575354) {
      throw new RuntimeException("Bad magic number");
    }
    if (readInt(in) != 0) {
      throw new RuntimeException("Bad version number");
    }
    int objectCount = readInt(in);
    objectPool = new SmallObject[objectCount];
    // Read headers to construct placeholder objects
    for (int i = 0; i < objectCount; i++) {
      int objType = in.read();
      switch (objType) {
        case 0:
          objectPool[i] = new SmallObject();
          break;
        case 1:
          objectPool[i] = new SmallInt(null, 0);
          break;
        case 2:
          objectPool[i] = new SmallByteArray(null, 0);
          break;
        default:
          throw new RuntimeException("Unknown object type " + objType);
      }
    }
    // Then fill in the objects
    for (int i = 0; i < objectCount; i++) {
      SmallObject obj = objectPool[i];
      obj.objClass = objectPool[readInt(in)];
      int dataLength = readInt(in);
      if (dataLength == -1) {
        obj.data = null;
      } else {
        obj.data = new SmallObject[dataLength];
        for (int j = 0; j < dataLength; j++) {
          obj.data[j] = objectPool[readInt(in)];
        }
      }
      // Type specific data
      if (obj instanceof SmallInt) {
        ((SmallInt) obj).value = readInt(in);
      }
      if (obj instanceof SmallByteArray) {
        SmallByteArray sba = (SmallByteArray) obj;
        int byteLength = readInt(in);
        sba.values = new byte[byteLength];
        for (int j = 0; j < byteLength; j++) {
          sba.values[j] = in.read();
        }
      }
    }
    numSmallInts = readInt(in);
    // Stream now points to the first root
  }

  public SmallInt[] readSmallInts() throws IOException {
    SmallInt[] ints = new SmallInt[numSmallInts];
    for (int i = 0; i < numSmallInts; i++) {
      ints[i] = (SmallInt) readObject();
    }
    return ints;
  }
}
