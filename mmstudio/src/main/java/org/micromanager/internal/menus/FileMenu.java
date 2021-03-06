package org.micromanager.internal.menus;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import org.micromanager.data.Datastore;
import org.micromanager.data.internal.SciFIODataProvider;
import org.micromanager.display.DisplayWindow;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.ReportingUtils;



/**
 * Handles setting up the File menu and its actions.
 */
public final class FileMenu {
   private static final String FILE_HISTORY = "list of recently-viewed files";
   private static final int MAX_HISTORY_SIZE = 15;
   private final MMStudio studio_;

   public FileMenu(MMStudio studio, JMenuBar menuBar) {
      studio_ = studio;

      // We generate the menu contents on the fly, as the "open recent"
      // menu items are dynamically-generated.
      final JMenu fileMenu = GUIUtils.createMenuInMenuBar(menuBar, "File");
      fileMenu.addMenuListener(new MenuListener() {
         @Override
         public void menuSelected(MenuEvent e) {
            fileMenu.removeAll();
            populateMenu(fileMenu);
            fileMenu.revalidate();
            fileMenu.repaint();
         }

         @Override
         public void menuDeselected(MenuEvent e) {
         }
         @Override
         public void menuCanceled(MenuEvent e) {
         }
      });
   }

   /**
    * Fill in the contents of the menu, including the "open recent" dynamic
    * menus.
    */
   private void populateMenu(JMenu fileMenu) {
      GUIUtils.addMenuItem(fileMenu, "Open (Virtual)...", null,
         new Runnable() {
            @Override
            public void run() {
               promptToOpenFile(true);
            }
      });

      fileMenu.add(makeOpenRecentMenu(true));

      GUIUtils.addMenuItem(fileMenu, "Open (RAM)...", null,
         new Runnable() {
            @Override
            public void run() {
               promptToOpenFile(false);
            }
      });

      fileMenu.add(makeOpenRecentMenu(false));
      
      GUIUtils.addMenuItem(fileMenu, "Open (SciFIO)...", null, () -> {
         openSciFIO();
      });

      fileMenu.addSeparator();

      GUIUtils.addMenuItem(fileMenu, "Close All Images...", null,
         new Runnable() {
            @Override
            public void run() {
               studio_.displays().promptToCloseWindows();
            }
      });

      fileMenu.addSeparator();

      GUIUtils.addMenuItem(fileMenu, "Exit", null,
         new Runnable() {
            @Override
            public void run() {
               studio_.closeSequence(false);
            }
         }
      );
   }

   private void promptToOpenFile(final boolean isVirtual) {
      new Thread(new Runnable() {
         @Override
         public void run() {
            try {
               Datastore store = studio_.data().promptForDataToLoad(
                       MMStudio.getFrame(), isVirtual);
               if (store == null) {
                  // User cancelled.
                  return;
               }
               if (store.getAnyImage() == null) {
                  studio_.logs().showError("Unable to load any images; file may be invalid.");
                  return;
               }
               // Note: the order is important, since the Inspector will only 
               // become aware of the display once its store is managed.
               studio_.displays().manage(store);
               studio_.displays().loadDisplays(store);
               updateFileHistory(store.getSavePath());
            } catch (IOException ex) {
               // ugly overloading of IOException to indicate user cancelling.
               if (!ex.getMessage().equals("User Canceled")) {
                  ReportingUtils.showError(ex, "There was an error when opening data");
               }
            }
         }
      }).start();
   }
   
   
   private void openSciFIO() {
      File file = FileDialogs.openFile(null,
              "Please select an image data set", FileDialogs.SCIFIO_DATA);
      if (file != null) {
         new Thread(new Runnable() {
            @Override
            public void run() {
               try {
                  SciFIODataProvider sdp = new SciFIODataProvider(studio_, file.getAbsolutePath());
                  if (sdp.getAnyImage() == null) {
                     studio_.logs().showError("Unable to load images");
                  }
                  DisplayWindow display = studio_.displays().createDisplay(sdp);
                  studio_.displays().addViewer(display);
               } catch (IOException ioe) {
                  // ugly overloading of IOException to indicate user cancelling.
                  if (!ioe.getMessage().equals("User Canceled")) {
                     studio_.logs().showError(ioe, "There was an error while opening data");
                  }
               }
            }
         }).start();
      }
   }

   private JMenu makeOpenRecentMenu(final boolean isVirtual) {
      JMenu result = new JMenu(String.format("Open Recent (%s)",
               isVirtual ? "Virtual" : "RAM"));

      String[] history = getRecentFiles();
      ArrayList<String> files = new ArrayList<String>(Arrays.asList(history));
      // History is from oldest to newest; we want newest to oldest so new
      // files are on top.
      Collections.reverse(files);
      for (final String path : files) {
         JMenuItem item = new JMenuItem(path);
         item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               new Thread(new Runnable() {
                  @Override
                  public void run() {
                     try {
                        MMStudio internalStudio = (MMStudio) studio_;
                        Datastore store = studio_.data().loadData( 
                                 internalStudio.getApplication().getMainWindow(), 
                                 path,
                                 isVirtual);
                        if (store != null && store.getAnyImage() != null) {
                           studio_.displays().manage(store);
                           studio_.displays().loadDisplays(store);
                        }
                        else {
                           studio_.logs().showError("Unable to load any images; file may be invalid or missing.");
                        }
                        updateFileHistory(path);
                     }
                     catch (IOException ioe) {
                        // ugly overloading of IOException to indicate user cancelling.
                        if (!ioe.getMessage().equals("User Canceled")) {
                           ReportingUtils.showError(ioe, "There was an error while opening data");
                        }
                     }
                  }
               }).start();
            }
         });
         result.add(item);
      }
      return result;
   }

   /**
    * Whenever a file is opened, add it to our history of recently-opened
    * files.
    * @param newFile file to be added to history
    */
   public void updateFileHistory(String newFile) {
      String[] fileHistory = getRecentFiles();
      ArrayList<String> files = new ArrayList<String>(Arrays.asList(fileHistory));
      if (files.contains(newFile)) {
         // It needs to go on the end; remove it from the middle.
         files.remove(newFile);
      }
      // Max length is 15; oldest at the front. Truncate the array as needed.
      while (files.size() > MAX_HISTORY_SIZE - 1) {
         files.remove(files.get(0));
      }
      files.add(newFile);
      setRecentFiles(files.toArray(fileHistory));
   }

   private static String[] getRecentFiles() {
      return MMStudio.getInstance().profile().getStringArray(
            FileMenu.class, FILE_HISTORY, new String[] {});
   }

   private static void setRecentFiles(String[] files) {
      MMStudio.getInstance().profile().setStringArray(
            FileMenu.class, FILE_HISTORY, files);
   }
}
