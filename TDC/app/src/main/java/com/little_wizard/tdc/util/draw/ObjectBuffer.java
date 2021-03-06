package com.little_wizard.tdc.util.draw;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.List;

/**
 * ObjectBuffer - 한 개의 이미지에서 분할 모델링 할 좌표 리스트들 저장
 * Element - 크롭된 텍스쳐, 크롭된 좌표 리스트 가짐
 * List<Element>
 */
public class ObjectBuffer {
    public class Element {
        Bitmap bitmap;
        List<Coordinates> list = new ArrayList<>();
        String axis;

        public Element(Bitmap bitmap, List<Coordinates> list, String axis) {
            this.bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
            if (list == null) {
                this.list = null;
            } else this.list.addAll(list);
            this.axis = axis;
        }

        public Bitmap getBitmap() {
            return bitmap;
        }

        public List<Coordinates> getList() {
            return list;
        }
        public String getAxis() {
            return axis;
        }
    }

    String filename, filepath;
    List<Element> buffer;
    Bitmap originalImage;
    String mode;

    public ObjectBuffer(String filepath, String filename, Bitmap bitmap) {
        this.filepath = filepath;
        this.filename = filename;
        originalImage = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        buffer = new ArrayList<>();
    }

    public void push(Bitmap bitmap, List<Coordinates> list, String axis) {
        List<Coordinates> newList = null;
        if (list != null) {
            newList = new ArrayList<>();
            newList.addAll(list);
        }
        Element newElement = new Element(bitmap.copy(Bitmap.Config.ARGB_8888, true), newList, axis);
        buffer.add(newElement);
    }

    public void remove(int index) {
        buffer.remove(index);
    }

    public List<Element> getBuffer() {
        return buffer;
    }

    public void setName(String name) {
        filename = name;
    }

    public String getName() {
        return filename;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getMode(){
        return mode;
    }

    public Element getElement(int index) {
        return buffer.get(index);
    }

    public Bitmap getOriginalImage() {
        return originalImage;
    }
}
