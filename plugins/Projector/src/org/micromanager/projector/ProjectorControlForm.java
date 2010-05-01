/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * ProjectorControlForm.java
 *
 * Created on Apr 3, 2010, 12:37:36 PM
 */

package org.micromanager.projector;

import org.micromanager.utils.GUIUtils;

/**
 *
 * @author arthur
 */
public class ProjectorControlForm extends javax.swing.JFrame {
   private final ProjectorController controller_;
   private final ProjectorPlugin plugin_;

    /** Creates new form ProjectorControlForm */
    public ProjectorControlForm(ProjectorPlugin plugin, ProjectorController controller) {
        initComponents();
        controller_ = controller;
        plugin_ = plugin;
        GUIUtils.recallPosition(this);
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
   // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
   private void initComponents() {

      calibrateButton = new javax.swing.JButton();
      setRoiButton = new javax.swing.JButton();
      onButton = new javax.swing.JButton();
      offButton = new javax.swing.JButton();
      allPixelsButton = new javax.swing.JButton();

      setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
      setTitle("Projector Controls");
      setResizable(false);

      calibrateButton.setText("Calibrate");
      calibrateButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            calibrateButtonActionPerformed(evt);
         }
      });

      setRoiButton.setText("Set ROI");
      setRoiButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            setRoiButtonActionPerformed(evt);
         }
      });

      onButton.setText("On");
      onButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            onButtonActionPerformed(evt);
         }
      });

      offButton.setText("Off");
      offButton.setSelected(true);
      offButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            offButtonActionPerformed(evt);
         }
      });

      allPixelsButton.setText("All Pixels");
      allPixelsButton.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            allPixelsButtonActionPerformed(evt);
         }
      });

      org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
      getContentPane().setLayout(layout);
      layout.setHorizontalGroup(
         layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
         .add(layout.createSequentialGroup()
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
               .add(layout.createSequentialGroup()
                  .add(onButton)
                  .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                  .add(offButton))
               .add(layout.createSequentialGroup()
                  .addContainerGap()
                  .add(setRoiButton, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 108, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)))
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
               .add(layout.createSequentialGroup()
                  .addPreferredGap(org.jdesktop.layout.LayoutStyle.UNRELATED)
                  .add(allPixelsButton)
                  .addContainerGap(123, Short.MAX_VALUE))
               .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
                  .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
                  .add(calibrateButton)
                  .addContainerGap())))
      );
      layout.setVerticalGroup(
         layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
         .add(layout.createSequentialGroup()
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
               .add(onButton)
               .add(offButton)
               .add(allPixelsButton))
            .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
               .add(setRoiButton)
               .add(calibrateButton))
            .addContainerGap(org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
      );

      pack();
   }// </editor-fold>//GEN-END:initComponents

    private void calibrateButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_calibrateButtonActionPerformed
       controller_.calibrate();
    }//GEN-LAST:event_calibrateButtonActionPerformed

    private void setRoiButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_setRoiButtonActionPerformed
       controller_.setRoi();
    }//GEN-LAST:event_setRoiButtonActionPerformed

    private void onButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_onButtonActionPerformed
       controller_.turnOn();
       offButton.setSelected(false);
       onButton.setSelected(true);
    }//GEN-LAST:event_onButtonActionPerformed

    private void offButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_offButtonActionPerformed
       controller_.turnOff();
       offButton.setSelected(true);
       onButton.setSelected(false);
    }//GEN-LAST:event_offButtonActionPerformed

    private void allPixelsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_allPixelsButtonActionPerformed
       controller_.activateAllPixels();
    }//GEN-LAST:event_allPixelsButtonActionPerformed

   public void dispose() {
      GUIUtils.storePosition(this);
      super.dispose();
   }
   
    private void formWindowClosing(java.awt.event.WindowEvent evt) {
       GUIUtils.storePosition(this);
       plugin_.dispose();
    }

   // Variables declaration - do not modify//GEN-BEGIN:variables
   private javax.swing.JButton allPixelsButton;
   private javax.swing.JButton calibrateButton;
   private javax.swing.JButton offButton;
   private javax.swing.JButton onButton;
   private javax.swing.JButton setRoiButton;
   // End of variables declaration//GEN-END:variables

}
