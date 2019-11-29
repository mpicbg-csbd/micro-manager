package net.haesleinhuepf.clij.mm;

import ij.IJ;
import ij.ImagePlus;
import net.haesleinhuepf.clij.CLIJ;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij.converters.FallBackCLIJConverterService;
import net.haesleinhuepf.clij.converters.implementations.ClearCLBufferToImagePlusConverter;
import net.haesleinhuepf.clij.macro.CLIJHandler;
import net.haesleinhuepf.clij.mm.converters.ImageToClearCLBufferConverter;
import net.haesleinhuepf.clijx.CLIJx;
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
    static CLIJMM instance = null;
    private AcquisitionWrapperEngine acquistionEngine;

    private CLIJMM() {}

    public static CLIJMM getInstance() {
        if (instance == null) {
            instance = new CLIJMM();
        }
        return instance;
    }
    long imageCounter = 0;
    String dataSetName = "";
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


    }

    public void imageArrived(DataProvider provider) {

        long numberOfImagesPerStack = (long) (((acquistionEngine.getSliceZTopUm() - acquistionEngine.getSliceZBottomUm()) / acquistionEngine.getSliceZStepUm()) + 1);

        IJ.log("Image arrived: " + (imageCounter) + "/" + numberOfImagesPerStack);

        long imageNumber = provider.getNumImages();
        try {
            Image image = provider.getAnyImage();

            //IJ.log("image: " + image);
            //IJ.log("imagec.class: " + image.getClass());

            //IJ.log("image z: " + image.getMetadata().getZPositionUm());

            CLIJx clijx = CLIJx.getInstance();
            ImageToClearCLBufferConverter converter = new ImageToClearCLBufferConverter();
            converter.setCLIJ(clijx.getClij());
            ClearCLBuffer buffer = converter.convert(image);
            collect(clijx, buffer, imageCounter, numberOfImagesPerStack, image.getMetadata());
            buffer.close();

        } catch (IOException e) {
            e.printStackTrace();
            IJ.log(e.getMessage());
        }
        imageCounter++;



    }

    private ClearCLBuffer stack = null;
    private void collect(CLIJx clijx, ClearCLBuffer buffer, long imageCounter, long numberOfImagesPerStack, Metadata metadata) {
        IJ.log("Collecting " + buffer);

        if (stack == null) {
            stack = clijx.create(new long[]{buffer.getWidth(), buffer.getHeight(), numberOfImagesPerStack}, buffer.getNativeType());
        }
        IJ.log("Stack " + stack);

        clijx.copySlice(buffer, stack, (int)(imageCounter % numberOfImagesPerStack));
        IJ.log("Copied " + stack);

        if ((imageCounter + 1) % numberOfImagesPerStack == 0 && imageCounter > 0) {
            // end of stack reached
            processStack(clijx, stack, imageCounter / numberOfImagesPerStack);
        }

    }

    private void processStack(CLIJx clijx, ClearCLBuffer stack, long timepoint) {
        IJ.log("eng" + acquistionEngine.getRootName());
        IJ.log("Root" + acquistionEngine.getRootName());

        String targetRootFolder = acquistionEngine.getRootName() + "/" + dataSetName + "/default/";
        targetRootFolder = targetRootFolder.replace("\\", "/");
        targetRootFolder = targetRootFolder.replace("//", "/");

        String targetFilename = "000000" + timepoint + ".tif";
        targetFilename = targetFilename.substring(targetFilename.length() - 6);

        IJ.log("Saving " + stack);
        IJ.log("Saving to " + targetRootFolder + targetFilename);
        String filename = targetRootFolder + targetFilename;

        ClearCLBufferToImagePlusConverter converter = new ClearCLBufferToImagePlusConverter();
        converter.setCLIJ(clijx.getClij());

        ImagePlus imp = converter.convert(stack);
        IJ.log("imp " + imp);

        new File(filename).getParentFile().mkdirs();

        IJ.saveAsTiff(imp, filename);

        // clijx.saveAsTIF(stack, targetRootFolder + targetFilename);
        IJ.log("done");
    }


    private MMAcquisition acquistion;
    public void setAcquisition(MMAcquisition mmAcquisition) {
        this.acquistion = mmAcquisition;
    }

    public void setAcquisitionEngine(AcquisitionWrapperEngine acqEng) {
        acquistionEngine = acqEng;
    }
}
