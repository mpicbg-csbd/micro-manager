package net.haesleinhuepf.clij.mm;

import ij.IJ;
import ij.ImagePlus;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij.converters.implementations.ClearCLBufferToImagePlusConverter;
import net.haesleinhuepf.clij.mm.converters.ImageToClearCLBufferConverter;
import net.haesleinhuepf.clijx.CLIJx;
import net.imglib2.realtransform.AffineTransform3D;
import org.micromanager.acquisition.internal.AcquisitionWrapperEngine;
import org.micromanager.acquisition.internal.MMAcquisition;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * CLIJMM
 * <p>
 * <p>
 * <p>
 * Author: @haesleinhuepf
 * 11 2019
 */
public class CLIJMM {

    public static boolean debug = true;

    public boolean doCLIJPostProcessing = true;

    public boolean denoiseStack = true;
    public boolean unsweepStack = true;
    public boolean saveStackTifs = true;
    public boolean saveMaximumProjectionTifs = true;

    static CLIJMM instance = null;
    private AcquisitionWrapperEngine acquistionEngine;
    public double unsweepAngle = 35;
    public double unsweepTranslationX = 0;
    public int denoiseMedianKernelSize = 3;

    private CLIJMM() {}

    public static CLIJMM getInstance() {
        if (instance == null) {
            instance = new CLIJMM();
        }
        return instance;
    }
    long imageCounter = 0;
    String dataSetName = "";
    String targetRootFolder = "";
    public void reset() {
        imageCounter = 0;
        if (stack != null) {
            stack.close();
        }
        CLIJx.getInstance().clear();
        stack = null;

        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
        Date date = new Date();
        dataSetName = dateFormat.format(date) + "_" + acquistionEngine.getDirName();

        targetRootFolder = acquistionEngine.getRootName() + "/" + dataSetName + "/";
        targetRootFolder = targetRootFolder.replace("\\", "/");
        targetRootFolder = targetRootFolder.replace("//", "/");

    }

    public void imageArrived(Image image) {
        if (!doCLIJPostProcessing) {
            return;
        }
        long numberOfImagesPerStack = (long) (((acquistionEngine.getSliceZTopUm() - acquistionEngine.getSliceZBottomUm()) / acquistionEngine.getSliceZStepUm()) + 1);

        if (debug) IJ.log("Image arrived: " + (imageCounter) + "/" + numberOfImagesPerStack);

        CLIJx clijx = CLIJx.getInstance();
        ImageToClearCLBufferConverter converter = new ImageToClearCLBufferConverter();
        converter.setCLIJ(clijx.getClij());
        ClearCLBuffer buffer = converter.convert(image);
        collect(clijx, buffer, imageCounter, numberOfImagesPerStack, image.getMetadata());
        buffer.close();

        imageCounter++;
    }

    private ClearCLBuffer stack = null;
    private void collect(CLIJx clijx, ClearCLBuffer buffer, long imageCounter, long numberOfImagesPerStack, Metadata metadata) {
        if (debug) IJ.log("Collecting " + buffer);

        if (stack == null) {
            stack = clijx.create(new long[]{buffer.getWidth(), buffer.getHeight(), numberOfImagesPerStack}, buffer.getNativeType());
        }
        if (debug) IJ.log("Stack " + stack);

        clijx.copySlice(buffer, stack, (int)(imageCounter % numberOfImagesPerStack));
        if (debug) IJ.log("Copied " + stack);

        if ((imageCounter + 1) % numberOfImagesPerStack == 0 && imageCounter > 0) {
            // end of stack reached
            if (debug) IJ.log("processing...");
            processStack(clijx, stack, imageCounter / numberOfImagesPerStack);
        }

    }

    private void processStack(CLIJx clijx, ClearCLBuffer stack, long timepoint) {
        if (debug) IJ.log("Root" + acquistionEngine.getRootName());

        ClearCLBuffer buffer = clijx.create(stack);
        // clijx.show(stack, "original");
        if (denoiseStack) {
            denoise(clijx, stack, buffer);
            ClearCLBuffer temp = stack;
            stack = buffer;
            buffer = temp;
        }
        // clijx.show(stack, "denoised");

        if (unsweepStack) {
            unsweep(clijx, stack, buffer);
            ClearCLBuffer temp = stack;
            stack = buffer;
            buffer = temp;
        }
        // clijx.show(stack, "unswept");
        if (saveStackTifs) saveStack(clijx, stack, timepoint);
        if (saveMaximumProjectionTifs) saveProjection(clijx, stack, timepoint);

        // cleanup
        if (buffer != null) {
            if (buffer == this.stack) {
                clijx.release(stack);
            } else {
                clijx.release(buffer);
            }
        }
    }

    private void denoise(CLIJx clijx, ClearCLBuffer stack, ClearCLBuffer buffer) {
        if (debug) IJ.log("denoise");
        clijx.medianSliceBySliceSphere(stack, buffer, denoiseMedianKernelSize, denoiseMedianKernelSize);
        if (debug) IJ.log("denoised");
    }

    private void unsweep(CLIJx clijx, ClearCLBuffer input, ClearCLBuffer output) {
        // todo: remove type casts after updating unsweep
        if (debug) IJ.log("unsweep");
        AffineTransform3D at = new AffineTransform3D();
        if (debug) IJ.log("unsweep 1");
        at.translate(new double[]{(double)(input.getWidth() / 2L + (long)unsweepTranslationX), 0.0D, 0.0D});
        double shear = 1.0D / Math.tan((double)unsweepAngle * 3.141592653589793D / 180.0D);

        if (debug) IJ.log("unsweep 2");

        AffineTransform3D shearTransform = new AffineTransform3D();

        if (debug) IJ.log("unsweep 3");

        shearTransform.set(1.0D, 0, 0);
        shearTransform.set(1.0D, 1, 1);
        shearTransform.set(1.0D, 2, 2);
        shearTransform.set(-shear, 2, 0);
        at.concatenate(shearTransform);

        if (debug) IJ.log("unsweep 4 ");

        clijx.affineTransform3D(input, output, at);

        //Unsweep.unsweep(clijx.getClij(), stack, buffer, (float)unsweepAngle, (int)unsweepTranslationX);
        if (debug) IJ.log("unswept");
    }

    private void saveStack(CLIJx clijx, ClearCLBuffer stack, long timepoint) {
        String targetFilename = "000000" + timepoint + ".tif";
        targetFilename = targetFilename.substring(targetFilename.length() - 6);

        if (debug) IJ.log("Saving " + stack);
        if (debug) IJ.log("Saving to " + targetRootFolder + "default/" + targetFilename);
        String filename = targetRootFolder + "default/" + targetFilename;

        ClearCLBufferToImagePlusConverter converter = new ClearCLBufferToImagePlusConverter();
        converter.setCLIJ(clijx.getClij());

        ImagePlus imp = converter.convert(stack);
        if (debug) IJ.log("imp " + imp);

        new File(filename).getParentFile().mkdirs();

        IJ.saveAsTiff(imp, filename);

        // clijx.saveAsTIF(stack, targetRootFolder + targetFilename);
        if (debug) IJ.log("saving done");
    }

    private void saveProjection(CLIJx clijx, ClearCLBuffer stack, long timepoint) {
        String targetFilename = "000000" + timepoint + ".tif";
        targetFilename = targetFilename.substring(targetFilename.length() - 6);

        if (debug) IJ.log("Saving " + stack);
        if (debug) IJ.log("Saving to " + targetRootFolder + "max_proj/" + targetFilename);
        String filename = targetRootFolder + "max_proj/" + targetFilename;

        ClearCLBuffer projection = clijx.create(new long[]{stack.getWidth(), stack.getHeight()}, stack.getNativeType());
        clijx.maximumZProjection(stack, projection);

        ClearCLBufferToImagePlusConverter converter = new ClearCLBufferToImagePlusConverter();
        converter.setCLIJ(clijx.getClij());

        ImagePlus imp = converter.convert(projection);
        clijx.release(projection);
        if (debug) IJ.log("imp " + imp);

        new File(filename).getParentFile().mkdirs();

        IJ.saveAsTiff(imp, filename);

        // clijx.saveAsTIF(stack, targetRootFolder + targetFilename);
        if (debug) IJ.log("saving done");
    }

    private MMAcquisition acquistion;
    public void setAcquisition(MMAcquisition mmAcquisition) {
        this.acquistion = mmAcquisition;
    }

    public void setAcquisitionEngine(AcquisitionWrapperEngine acqEng) {
        acquistionEngine = acqEng;
    }
}
