///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display implementation
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.display.internal.inspector;

import com.bulenkov.iconloader.IconLoader;

import com.google.common.eventbus.Subscribe;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GraphicsConfiguration;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowAdapter;

import java.io.File;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Stack;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import net.miginfocom.swing.MigLayout;

import org.micromanager.display.DataViewer;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.Inspector;
import org.micromanager.display.InspectorPanel;
import org.micromanager.display.InspectorPlugin;
import org.micromanager.display.internal.DefaultDisplayManager;
import org.micromanager.display.DisplayDestroyedEvent;
import org.micromanager.display.internal.events.DisplayActivatedEvent;
import org.micromanager.events.DisplayAboutToShowEvent;
import org.micromanager.events.internal.DefaultEventManager;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.pluginmanagement.PluginSorter;
import org.micromanager.internal.utils.DefaultUserProfile;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.MMFrame;
import org.micromanager.internal.utils.ReportingUtils;


/**
 * This frame shows a set of controls that are related to the currently-on-top
 * DataViewer (or to a specific DataViewer as selected by the user). It
 * consists of a set of expandable panels in a vertical configuration.
 */
public class InspectorFrame extends MMFrame implements Inspector {

   /**
    * This class is used to represent entries in the dropdown menu the user
    * uses to select which DataViewer the InspectorFrame is controlling.
    * Functionally it serves as a mapping of DataViewer names to those
    * DataViewers, with the caveat that DataViewer names can change
    * any time a duplicate display is created or destroyed (see the note on
    * populateChooser(), below).
    * HACK: as a special case, if one of these is created with a null display,
    * then it pretends to be the TOPMOST_DISPLAY option instead.
    */
   private class DisplayMenuItem {
      private final DataViewer menuDisplay_;
      public DisplayMenuItem(DataViewer display) {
         menuDisplay_ = display;
      }

      public DataViewer getDisplay() {
         return menuDisplay_;
      }

      @Override
      public String toString() {
         if (menuDisplay_ != null) {
            return truncateName(menuDisplay_.getName(), 40);
         }
         return TOPMOST_DISPLAY;
      }
   }

   private static final String TOPMOST_DISPLAY = "Topmost Window";
   private static final String CONTRAST_TITLE = "Histograms and Settings";
   private static final String WINDOW_WIDTH = "width of the inspector frame";

   // This boolean is used to create a new Inspector frame only on the first
   // time that we create a new DisplayWindow in any given session of the
   // program.
   private static boolean haveCreatedInspector_ = false;
   public static boolean createFirstInspector() {
      if (!haveCreatedInspector_) {
         new InspectorFrame(null);
         return true;
      }
      return false;
   }

   private DataViewer display_;
   private final Stack<DataViewer> displayHistory_;
   private final ArrayList<InspectorPanel> panels_;
   private final JPanel contents_;
   private JComboBox displayChooser_;
   private JButton raiseButton_;
   private final JLabel curDisplayTitle_;

   public InspectorFrame(DataViewer display) {
      super();
      haveCreatedInspector_ = true;
      displayHistory_ = new Stack<DataViewer>();
      setTitle("Image Inspector");
      setAlwaysOnTop(true);
      // Use a small title bar.
      getRootPane().putClientProperty("Window.style", "small");

      contents_ = new JPanel(new MigLayout("flowy, insets 0, gap 0, fillx"));

      // Create a dropdown menu to select which display to show info/controls
      // for. By default, we show info on the topmost display (changing when
      // that display changes).
      displayChooser_ = new JComboBox();
      populateChooser();
      displayChooser_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            DisplayMenuItem item = (DisplayMenuItem) (displayChooser_.getSelectedItem());
            if (item.getDisplay() != null) {
               // In other words, the user didn't select the "topmost display"
               // option.
               setDisplay(item.getDisplay());
            }
            // Show the raise button only if the "topmost display" option
            // isn't set.
            raiseButton_.setVisible(item.getDisplay() != null);
         }
      });

      // Add a button for raising the currently-selected display to the top.
      // This button is only shown if the "topmost display" option is *not*
      // selected.
      raiseButton_ = new JButton("Raise");
      raiseButton_.setToolTipText("Bring the selected window to the front");
      raiseButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            if (display_ instanceof DisplayWindow) {
               ((DisplayWindow) display_).toFront();
            }
         }
      });
      raiseButton_.setVisible(false);

      contents_.add(new JLabel("Show info for:"), "flowx, split 3");
      contents_.add(displayChooser_);
      contents_.add(raiseButton_);

      curDisplayTitle_ = new JLabel("");
      curDisplayTitle_.setVisible(false);
      // This hidemode causes invisible elements to take up no space.
      contents_.add(curDisplayTitle_, "hidemode 2");

      JScrollPane scroller = new JScrollPane(contents_,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
      add(scroller);
      setVisible(true);

      panels_ = new ArrayList<InspectorPanel>();
      // Hard-coded initial panels.
      addPanel(CONTRAST_TITLE, new HistogramsPanel());
      addPanel("Metadata", new MetadataPanel());
      addPanel("Comments", new CommentsPanel());
      // Pluggable panels. Sort by name -- not the classpath name in the
      // given HashMap, but the name returned by getName();
      ArrayList<InspectorPlugin> plugins = new ArrayList<InspectorPlugin>(
            MMStudio.getInstance().plugins().getInspectorPlugins().values());
      Collections.sort(plugins, new PluginSorter());
      for (InspectorPlugin plugin : plugins) {
         addPanel(plugin.getName(), plugin.createPanel());
      }

      if (display != null) {
         displayChooser_.setSelectedItem(display.getName());
         setDisplay(display);
      }

      // We want to be in the upper-right corner of the primary display.
      GraphicsConfiguration config = GUIUtils.getGraphicsConfigurationContaining(1, 1);
      // Allocate enough width that the histograms look decent.
      setMinimumSize(new Dimension(400, 50));
      // HACK: don't know our width; just choose a vaguely-correct offset.
      loadAndRestorePosition(config.getBounds().width - 450, 0);
      setSize(new Dimension(getDefaultWidth(), getHeight()));

      DefaultEventManager.getInstance().registerForEvents(this);
      // Cleanup when window closes.
      addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent event) {
            cleanup();
         }
      });
      // Save the size when the user resizes the window, and the
      // position when the user moves it.
      addComponentListener(new ComponentAdapter() {
         @Override
         public void componentResized(ComponentEvent e) {
            setDefaultWidth((int) getSize().getWidth());
         }

         @Override
         public void componentMoved(ComponentEvent e) {
            savePosition();
         }
      });
   }

   /**
    * Fill in the display names in the display chooser. These can change
    * whenever a display is added or destroyed, as that display may be a
    * duplicate, causing the numbers to appear (e.g. when a duplicate of
    * Snap/Live is created, the old "Snap/Live View" becomes
    * "#1: Snap/Live View").
    */
   private void populateChooser() {
      // Disable the listener so selections don't change while we do this.
      ActionListener[] listeners = displayChooser_.getActionListeners();
      for (ActionListener listener : listeners) {
         displayChooser_.removeActionListener(listener);
      }
      DisplayMenuItem curItem = (DisplayMenuItem) (displayChooser_.getSelectedItem());

      displayChooser_.removeAllItems();
      // See the HACK note on DisplayMenuItem.
      DisplayMenuItem nullItem = new DisplayMenuItem(null);
      displayChooser_.addItem(nullItem);
      List<DataViewer> allDisplays = new ArrayList<DataViewer>(DefaultDisplayManager.getInstance().getAllDataViewers());
      for (DataViewer display : allDisplays) {
         if (!displayHistory_.contains(display)) {
            displayHistory_.push(display);
         }
         DisplayMenuItem newItem = new DisplayMenuItem(display);
         displayChooser_.addItem(newItem);
         if (display_ == display && curItem.getDisplay() != null) {
            // This is the display that we were previously targeting.
            displayChooser_.setSelectedItem(newItem);
         }
      }

      for (ActionListener listener : listeners) {
         displayChooser_.addActionListener(listener);
      }
   }

   /**
    * Add a new InspectorPanel to the window.
    * @param title Title of the panel
    * @param panel Inspector Panel to be added
    */
   public final void addPanel(String title, final InspectorPanel panel) {
      panels_.add(panel);
      panel.setInspector(this);
      // Wrap the panel in our own panel which includes the header.
      final JPanel wrapper = new JPanel(
            new MigLayout("flowy, insets 0, fillx"));
      wrapper.setBorder(BorderFactory.createRaisedBevelBorder());

      // Create a clickable header to show/hide contents, and hold a gear
      // menu if available.
      JPanel header = new JPanel(new MigLayout("flowx, insets 0, fillx",
               "[fill]push[]"));
      final JLabel label = new JLabel(title,
               UIManager.getIcon("Tree.collapsedIcon"),
               SwingConstants.LEFT);
      // Ignore day/night settings for the label text, since the background
      // (i.e. the header panel we're in) also ignores day/night settings.
      label.setForeground(new Color(50, 50, 50));
      header.add(label, "growx");

      final JPopupMenu gearMenu = panel.getGearMenu();
      final JButton gearButton;
      if (gearMenu != null) {
         gearButton = new JButton(IconLoader.getIcon(
                  "/org/micromanager/icons/gear.png"));
         gearButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
               // Regenerate the menu as it may have changed.
               panel.getGearMenu().show(gearButton, e.getX(), e.getY());
            }
         });
         header.add(gearButton, "growx 0, hidemode 2");
      }
      else {
         // Final variables must be set to *something*.
         gearButton = null;
      }

      header.setCursor(new Cursor(Cursor.HAND_CURSOR));
      header.setBackground(new Color(220, 220, 220));
      header.addMouseListener(new MouseAdapter() {
         @Override
         public void mouseClicked(MouseEvent e) {
            panel.setVisible(!panel.isVisible());
            if (panel.isVisible()) {
               wrapper.add(panel, "growx, gap 0");
               label.setIcon(UIManager.getIcon("Tree.expandedIcon"));
            }  
            else {
               wrapper.remove(panel);
               label.setIcon(UIManager.getIcon("Tree.collapsedIcon"));
            }  
            if (gearButton != null) {
               gearButton.setVisible(panel.isVisible());
            }
            pack();
         }
      });
      wrapper.add(header, "growx");
      // HACK: the specific panel with the "Contrast" title is automatically
      // visible.
      if (title.contentEquals(CONTRAST_TITLE)) {
         wrapper.add(panel, "growx, gap 0");
         panel.setVisible(true);
         label.setIcon(UIManager.getIcon("Tree.expandedIcon"));
      }
      else {
         panel.setVisible(false); // So the first click will show it.
      }

      contents_.add(wrapper, "growx");
      validate();
   }

   @Override
   public synchronized void relayout() {
      /**
       * HACK: we have to postpone our relayout logic until after all pending
       * events have cleared from the EDT, because Swing's revalidate logic
       * only kicks in once the EDT is clear, and we need to do our own
       * layout after Swing is done. At least, I *think* that's what's going
       * on. Bottom line is, if we do the below code immediately instead of
       * in an invokeLater(), then the window size is wrong.
       */
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            // HACK: coerce minimum size to our default size for the duration
            // of this pack; otherwise our default size effectively gets
            // ignored on a routine basis.
            Dimension minSize = getMinimumSize();
            int width = getDefaultWidth();
            setMinimumSize(new Dimension(width, (int) minSize.getHeight()));
            pack();
            setMinimumSize(minSize);
         }
      });
   }

   @Subscribe
   public void onNewDisplay(DisplayAboutToShowEvent event) {
      try {
         event.getDisplay().registerForEvents(this);
         populateChooser();
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Error adding new display to inspector");
      }
   }

   @Subscribe
   public void onDisplayDestroyed(DisplayDestroyedEvent event) {
      event.getDisplay().unregisterForEvents(this);
      populateChooser();
      setDisplay(null);
   }

   @Subscribe
   public void onDisplayActivated(DisplayActivatedEvent event) {
      DataViewer newDisplay = event.getDisplay();
      if (newDisplay.getIsClosed()) {

         // TODO: why do we get notified of this?
         return;
      }
      try {
         DisplayMenuItem item = (DisplayMenuItem) (displayChooser_.getSelectedItem());
         if (item.getDisplay() != null) {
            // We're keyed to a specific display, so we don't care that another
            // one is now on top.
            return;
         }
         if (display_ == newDisplay) {
            // We're already keyed to this display, so do nothing.
            return;
         }
         setDisplay(newDisplay);
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Error on new display activation");
      }
   }

   private void setDisplay(DataViewer display) {
      if (display == null) {
         // Remove the top display from the history, and switch to the most
         // recent next one, if possible.
         displayHistory_.remove(display_);
         if (!displayHistory_.empty()) {
            display = displayHistory_.peek();
         }
      }
      // Update the display history so the new display is in front.
      if (display != null) {
         if (displayHistory_.contains(display)) {
            displayHistory_.remove(display);
         }
         displayHistory_.push(display_);
      }
      // Update our listing of displays.
      display_ = display;
      populateChooser();
      for (InspectorPanel panel : panels_) {
         try {
            if (panel.getIsValid(display_)) {
               panel.setVisible(true);
               panel.setDataViewer(display_);
            }
            else {
               panel.setVisible(false);
            }
         }
         catch (Exception e) {
            ReportingUtils.logError(e, "Error dispatching new display to " + panel);
         }
      }
      if (display_ == null) {
         curDisplayTitle_.setText("No available display");
         curDisplayTitle_.setVisible(true);
      }
      else {
         DisplayMenuItem item = (DisplayMenuItem) (displayChooser_.getSelectedItem());
         if (item.getDisplay() == null) {
            // Show the title of the current display, to make it clear which one
            // we're controlling.
            String name = truncateName(display_.getName(), 60);
            curDisplayTitle_.setText(name);
            curDisplayTitle_.setVisible(true);
         }
         else {
            curDisplayTitle_.setVisible(false);
         }
      }
      // Redo the layout to account for curDisplayTitle_ being shown/hidden.
      validate();
   }

   /**
    * Truncate the provided display name so that elements that display it in
    * the frame don't cause the frame to get horribly stretched.
    */
   private String truncateName(String name, int maxLen) {
      if (name.length() > maxLen) {
         File file = new File(name);
         String finalName = file.getName();
         // Subtract 3 for the ellipses.
         int remainingLength = maxLen - finalName.length() - 3;
         name = name.substring(0, remainingLength) + "..." + finalName;
      }
      return name;
   }

   @Override
   public void dispose() {
      cleanup();
      super.dispose();
   }

   /**
    * Make certain that our panels get cleaned up and we don't leave any
    * references lying around.
    */
   private void cleanup() {
      for (InspectorPanel panel : panels_) {
         panel.cleanup();
      }
      savePosition();
      DefaultEventManager.getInstance().unregisterForEvents(
         InspectorFrame.this);
   }

   private static int getDefaultWidth() {
      return DefaultUserProfile.getInstance().getInt(
            InspectorFrame.class, WINDOW_WIDTH, 450);
   }

   private static void setDefaultWidth(int width) {
      DefaultUserProfile.getInstance().setInt(
            InspectorFrame.class, WINDOW_WIDTH, width);
   }
}
