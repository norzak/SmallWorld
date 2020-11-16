package smallworld.core;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.TreeMap;

class ImageWriter {
  private final TreeMap<Integer, SmallObject> allObjects;
  private int numSmallInts;
  private int objectIndex;
  private final HashMap<SmallObject, Integer> objectPool;
  private final OutputStream out;
  private final ArrayList<Integer> roots;

  public ImageWriter(OutputStream out) {
    objectPool = new HashMap<SmallObject, Integer>();
    allObjects = new TreeMap<Integer, SmallObject>();
    roots = new ArrayList<Integer>();
    this.out = out;
    this.objectIndex = 0;
    this.numSmallInts = 0;
  }
  
  //Prepare to write image to byte array instead of stream
  private void writeInt(OutputStream out, int value) throws IOException {
    out.write((value >> 24) & 0xFF);
    out.write((value >> 16) & 0xFF);
    out.write((value >> 8) & 0xFF);
    out.write(value & 0xFF);
  }

  public void finish() throws IOException {
    // Header, SWST version 0
    writeInt(out, 0x53575354); // 'SWST'
    writeInt(out,0); // version 0
    writeInt(out, objectIndex); // object count
    // First, write the object types
    // 0 = SmallObject, 1 = SmallInt, 2 = SmallByteArray
    for (Entry<Integer, SmallObject> entry : allObjects.entrySet()) {
      SmallObject obj = entry.getValue();
      if (obj instanceof SmallByteArray) {
        out.write(2);
      } else if (obj instanceof SmallInt) {
        out.write(1);
      } else if (obj instanceof SmallJavaObject) {
        throw new RuntimeException("JavaObject serialization not supported");
      } else {
        out.write(0);
      }
    }
    // Then, write entries
    for (Entry<Integer, SmallObject> entry : allObjects.entrySet()) {
      SmallObject obj = entry.getValue();
      // Reference to class
      writeInt(out,objectPool.get(obj.objClass));
      // data (-1 if none)
      if (obj.data == null) {
        writeInt(out,-1);
      } else {
        writeInt(out,obj.data.length);
        for (SmallObject child : obj.data) {
          writeInt(out,objectPool.get(child));
        }
      }
      if (obj instanceof SmallInt) {
        writeInt(out,((SmallInt) obj).value);
      }
      if (obj instanceof SmallByteArray) {
        SmallByteArray sba = (SmallByteArray) obj;
        writeInt(out,sba.values.length);
        for (byte b : sba.values) {
          out.write(b);
        }
      }
    }
    // Write the (special case) count of small integers
    writeInt(out,numSmallInts);
    // Finally, write out index of the roots, so they can be streamed back in
    for (Integer i : roots) {
      writeInt(out,i);
    }
    out.close();
  }

  public void writeObject(SmallInt[] ints) {
    if (numSmallInts > 0) {
      throw new RuntimeException("Can only write ints one time");
    }
    numSmallInts = ints.length;
    for (SmallInt child : ints) {
      writeObject(child);
    }
  }

  public void writeObject(SmallObject obj) {
    writeObjectImpl(obj);
    roots.add(objectPool.get(obj));
  }

  private void writeObjectImpl(SmallObject obj) {
    if (!objectPool.containsKey(obj)) {
      objectPool.put(obj, objectIndex);
      allObjects.put(objectIndex, obj);
      objectIndex++;
      writeObjectImpl(obj.objClass);
      if (obj.data != null) {
        for (SmallObject child : obj.data) {
          writeObjectImpl(child);
        }
      }
    }
  }
}
