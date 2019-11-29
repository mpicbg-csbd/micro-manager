package net.haesleinhuepf.clij.mm.converters;

import ij.IJ;
import ij.ImagePlus;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij.converters.AbstractCLIJConverter;
import net.haesleinhuepf.clij.converters.CLIJConverterPlugin;
import net.haesleinhuepf.clij.converters.implementations.RandomAccessibleIntervalToClearCLBufferConverter;
import net.haesleinhuepf.clij.coremem.enums.NativeTypeEnum;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import org.micromanager.data.Image;
import org.scijava.plugin.Plugin;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 *
 * Author: @haesleinhuepf
 *         November 2019
 */
@Plugin(type = CLIJConverterPlugin.class)
public class ImageToClearCLBufferConverter extends AbstractCLIJConverter<Image, ClearCLBuffer> {


    @Override
    public ClearCLBuffer convert(Image source) {
        //IJ.log("converting... ");
        //IJ.log("raw: " + source.getRawPixels());
        //IJ.log("raw class: " + source.getRawPixels().getClass().getName());

        Object raw = source.getRawPixels();
        //IJ.log("raw c: " + raw);
        //short[] ch = (short[]) raw;
        //IJ.log("ch " + ch);
        //IJ.log("ch[0] " + ch[0]);


        if (raw instanceof byte[]) {
            //IJ.log("byte[]");
            ClearCLBuffer buffer = clij.create(new long[]{source.getWidth(), source.getHeight()}, NativeTypeEnum.UnsignedByte);
            buffer.readFrom(ByteBuffer.wrap((byte[]) raw), true);
            return buffer;
        } else if (raw instanceof short[]) {
            //IJ.log("short[]");
            ClearCLBuffer buffer = clij.create(new long[]{source.getWidth(), source.getHeight()}, NativeTypeEnum.UnsignedShort);
            buffer.readFrom(ShortBuffer.wrap((short[]) raw), true);
            return buffer;
        } else if (raw instanceof float[]) {
            //IJ.log("float[]");
            ClearCLBuffer buffer = clij.create(new long[]{source.getWidth(), source.getHeight()}, NativeTypeEnum.Float);
            buffer.readFrom(FloatBuffer.wrap((float[]) raw), true);
            return buffer;
        } else if(raw instanceof ByteBuffer) {
            //IJ.log("byte");
            ClearCLBuffer buffer = clij.create(new long[]{source.getWidth(), source.getHeight()}, NativeTypeEnum.UnsignedByte);
            buffer.readFrom((ByteBuffer) raw, true);
            return buffer;
        } else if(raw instanceof ShortBuffer) {
            //IJ.log("short");
            ClearCLBuffer buffer = clij.create(new long[]{source.getWidth(), source.getHeight()}, NativeTypeEnum.UnsignedShort);
            buffer.readFrom((ShortBuffer) raw, true);
            return buffer;
        } else if(raw instanceof FloatBuffer) {
            //IJ.log("float");
            ClearCLBuffer buffer = clij.create(new long[]{source.getWidth(), source.getHeight()}, NativeTypeEnum.Float);
            buffer.readFrom((FloatBuffer) raw, true);
            return buffer;
        } else {
            //IJ.log("Source" );
            throw new IllegalArgumentException("ImageToClearCLBufferConverter doesn't support " +  source);
        }

    }

    @Override
    public Class<Image> getSourceType() {
        return Image.class;
    }

    @Override
    public Class<ClearCLBuffer> getTargetType() {
        return ClearCLBuffer.class;
    }
}
