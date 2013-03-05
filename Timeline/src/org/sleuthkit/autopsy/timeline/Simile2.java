/*
 * Autopsy Forensic Browser
 *
 * Copyright 2013 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.timeline;

import com.sun.javafx.application.PlatformImpl;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.Stack;
import java.util.logging.Level;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.JFXPanel;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.modules.InstalledFileLocator;
import org.openide.nodes.ChildFactory;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.openide.util.actions.Presenter;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corecomponents.DataContentPanel;
import org.sleuthkit.autopsy.corecomponents.DataResultPanel;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.datamodel.FilterNodeLeaf;
import org.sleuthkit.autopsy.datamodel.DirectoryNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNode;
import org.sleuthkit.autopsy.datamodel.DisplayableItemNodeVisitor;
import org.sleuthkit.autopsy.datamodel.FileNode;
import org.sleuthkit.autopsy.ingest.IngestManager;
import org.sleuthkit.autopsy.recentactivity.JavaSystemCaller;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Directory;
import org.sleuthkit.datamodel.File;
import org.sleuthkit.datamodel.FsContent;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskData;

@ActionID(category = "Tools", id = "org.sleuthkit.autopsy.timeline.Simile2")
@ActionRegistration(displayName = "#CTL_MakeTimeline")
@ActionReferences(value = {
    @ActionReference(path = "Menu/Tools", position = 100)})
@NbBundle.Messages(value = "CTL_TimelineView=Generate Timeline")
public class Simile2 extends CallableSystemAction implements Presenter.Toolbar, PropertyChangeListener {

    private static final Logger logger = Logger.getLogger(Simile2.class.getName());
    private final java.io.File macRoot = InstalledFileLocator.getDefault().locate("mactime", Simile2.class.getPackage().getName(), false);
    private JFrame jf;          //frame for holding all the elements
    private Group group_Charts; //Orders the charts
    private Scene scene_Charts; //Displays the charts
    private HBox hBox_Charts;      //Holds the navigation buttons in horiztonal fashion. 
    private VBox vBox_FX;        //Holds the JavaFX Elements in vertical fashion. 
    private JFXPanel panel_Charts;  //FX panel to hold the group
    private BarChart chart_Events;      //Yearly/Monthly events - Bar chart
    private ScrollPane scroll_Events;  //Scroll Panes for dealing with oversized an oversized chart
    private final int Height_Frame = 850; //Sizing constants
    private final int Width_Frame = 1300;
    private Button zoomOutButton;  //Navigation buttons
    private ComboBox<String> dropdown_SelectYears; //Dropdown box for selecting years. Useful when the charts' scale means some years are unclickable, despite having events.
    private final Stack<BarChart> stack_PrevCharts = new Stack<BarChart>();  //Stack for storing drill-up information.
    private BarChart chart_TopLevel; //the topmost chart, used for resetting to default view.
    private DataResultPanel dataResult;
    private DataContentPanel dataContentPanel;
    private ProgressHandle progress;
    private java.io.File moduleDir;
    private String mactimeFileName;
    private List<YearEpoch> data;
    private boolean listeningToAddImage = false;
    private long lastObjectId = -1;
    private TimelineProgressDialog progressDialog;
    private EventHandler mouseEnteredListener;
    private EventHandler mouseExitedListener;

    //Swing components and JavafX components don't play super well together
    //Swing components need to be initialized first, in the swing specific thread
    //Next, the javafx components may be initialized.
    private void customizeSwing() {

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                dataContentPanel = new DataContentPanel();
                dataResult = DataResultPanel.createInstance("Timeline Results", "", Node.EMPTY, 0, dataContentPanel);
                dataResult.setContentViewer(new DataContentPanel());
                dataResult.setAlignmentX(Component.LEFT_ALIGNMENT);
                dataResult.setPreferredSize(new Dimension(700, 300));
                logger.log(Level.INFO, "Successfully created viewers");
            }
        });
    }

    private void customize() {

        //listeners
        mouseEnteredListener = new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent e) {
                panel_Charts.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }
        };
        mouseExitedListener = new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent e) {
                panel_Charts.setCursor(null);
            }
        };

        //Making the main frame *
        jf = new JFrame(Case.getCurrentCase().getName() + " - Autopsy Timeline (Beta)");

        //use the same icon on jframe as main application
        jf.setIconImage(WindowManager.getDefault().getMainWindow().getIconImage());
        jf.setSize(Width_Frame, Height_Frame); //(Width, Height)

        //JPanels are used as the cohesive glue that binds everything together.*/
        //The chartJpanel holds the chart, 
        //aligned vertically (Y_AXIS)
        final JPanel chartJPanel = new JPanel();
        chartJPanel.setLayout(new BoxLayout(chartJPanel, BoxLayout.Y_AXIS));

        //ViewerJPanel holds both of the DataResult/DataContent viewers,
        //aligned horizontally (X_AXIS)
        final JPanel viewerJPanel = new JPanel();
        viewerJPanel.setLayout(new BoxLayout(viewerJPanel, BoxLayout.X_AXIS));

        //ComboJPanel holds both of the above JPanels together,
        //aligned vertically (Y_AXIS)

        // create a horizontal and vertical split panes


        final JSplitPane splitYPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chartJPanel, viewerJPanel);
        splitYPane.setDividerLocation(Height_Frame / 2);

        runJavaFxThread(chartJPanel, viewerJPanel, splitYPane);
    }

    private void runJavaFxThread(final JPanel chartJPanel, final JPanel viewerJPanel,
            final JSplitPane splitYPane) {
        //JavaFX thread
        //JavaFX components MUST be run in the JavaFX thread, otherwise massive amounts of exceptions will be thrown and caught. Liable to freeze up and crash.
        //Components can be declared whenever, but initialization and manipulation must take place here.
        PlatformImpl.startup(new Runnable() {
            @Override
            public void run() {
                try {
                    // start the progress bar
                    progress = ProgressHandleFactory.createHandle("Creating timeline . . .");
                    progress.start();

                    panel_Charts = new JFXPanel();
                    group_Charts = new Group();
                    scene_Charts = new Scene(group_Charts, Width_Frame, Math.round(Height_Frame / .75)); //Width, Height
                    vBox_FX = new VBox(5);
                    vBox_FX.setAlignment(Pos.BOTTOM_CENTER);
                    hBox_Charts = new HBox(10);
                    hBox_Charts.setAlignment(Pos.BOTTOM_CENTER);

                    //Initializing default values for the scroll pane
                    scroll_Events = new ScrollPane();
                    scroll_Events.setPrefSize(Width_Frame, Math.round(Height_Frame / .75)); //Width, Height
                    scroll_Events.setContent(null); //Needs some content, otherwise it crashes

                    // set up moduleDir
                    moduleDir = new java.io.File(Case.getCurrentCase().getModulesOutputDirAbsPath() + java.io.File.separator + "timeline");
                    if (!moduleDir.exists()) {
                        moduleDir.mkdir();
                    }

                    int currentProgress = 0;
                    java.io.File mactimeFile = new java.io.File(moduleDir, mactimeFileName);
                    if (!mactimeFile.exists()) {
                        progressDialog.setProgressTotal(3); //total 3 units
                        logger.log(Level.INFO, "Creating mactime file: " + mactimeFile.getAbsolutePath());
                        progressDialog.updateProgressBar("Generating body file");
                        String bodyFilePath = makeBodyFile();
                        progressDialog.updateProgressBar(++currentProgress);
                        progressDialog.updateProgressBar("Generating mactime file");
                        makeMacTime(bodyFilePath);
                        progressDialog.updateProgressBar(++currentProgress);
                        data = null;
                    } else {
                        progressDialog.setProgressTotal(1); //total 1 units
                        logger.log(Level.INFO, "Mactime file already exists; parsing that: " + mactimeFile.getAbsolutePath());
                    }
                    
                    
                    progressDialog.updateProgressBar("Parsing Mactime");
                    if (data == null) {
                        data = parseMacTime(mactimeFile); //The sum total of the mactime parsing.  YearEpochs contain everything you need to make a timeline.
                    }
                    progressDialog.updateProgressBar(++currentProgress);

                    //Making a dropdown box to select years.
                    List<String> lsi = new ArrayList<String>();  //List is in the format of {Year : Number of Events}, used for selecting from the dropdown.
                    for (YearEpoch ye : data) {
                        lsi.add(ye.year + " : " + ye.getNumFiles());
                    }
                    ObservableList<String> listSelect = FXCollections.observableArrayList(lsi);
                    dropdown_SelectYears = new ComboBox(listSelect);

                    //Buttons for navigating up and down the timeline
                    zoomOutButton = new Button("Zoom Out");
                    zoomOutButton.setOnAction(new EventHandler<ActionEvent>() {
                        @Override
                        public void handle(ActionEvent e) {
                            BarChart bc;
                            if (stack_PrevCharts.size() == 0) {
                                bc = chart_TopLevel;
                            } else {
                                bc = stack_PrevCharts.pop();
                            }
                            chart_Events = bc;
                            scroll_Events.setContent(chart_Events);
                        }
                    });

                    dropdown_SelectYears.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
                        @Override
                        public void changed(ObservableValue<? extends String> ov, String t, String t1) {
                            if (dropdown_SelectYears.getValue() != null) {
                                chartJPanel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                                try {
                                    chart_Events = createMonthsWithDrill(findYear(data, Integer.valueOf(dropdown_SelectYears.getValue().split(" ")[0])));
                                    scroll_Events.setContent(chart_Events);
                                } finally {
                                    chartJPanel.setCursor(null);
                                }
                            }
                        }
                    });

                    //Adding things to the V and H boxes. 
                    //hBox_Charts stores the pseudo menu bar at the top of the timeline. |Zoom Out|View Year: [Select Year]|►|
                    hBox_Charts.getChildren().addAll(zoomOutButton, new Label("Go To:"), dropdown_SelectYears);
                    vBox_FX.getChildren().addAll(hBox_Charts, scroll_Events); //FxBox_V holds things in a visual stack. 
                    group_Charts.getChildren().add(vBox_FX); //Adding the FxBox to the group. Groups make things easier to manipulate without having to update a hundred things every change.
                    panel_Charts.setScene(scene_Charts);


                    panel_Charts.setAlignmentX(Component.LEFT_ALIGNMENT);

                    chartJPanel.add(panel_Charts);
                    //viewerJPanel.add(dataResult);

                    final JSplitPane splitXPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, dataResult, dataContentPanel);
                    splitXPane.setDividerLocation(Width_Frame / 2);
                    viewerJPanel.add(splitXPane);
                    chartJPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
                    viewerJPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

                    chart_TopLevel = createYearChartWithDrill(data);
                    chart_Events = chart_TopLevel;
                    scroll_Events.setContent(chart_Events);

                    jf.add(splitYPane);
                    jf.setVisible(true);
                } finally {
                    // stop the progress bar
                    progress.finish();

                    // close the progressDialog
                    progressDialog.doClose(0);
                }
            }
        });
    }

    /**
     * Creates a BarChart with datapoints for all the years from the parsed
     * mactime file.
     *
     * @param allYears The list of years that have barData from the mactime file
     * @return BarChart scaled to the year level
     */
    private BarChart createYearChartWithDrill(final List<YearEpoch> allYears) {
        final CategoryAxis xAxis = new CategoryAxis(); //Axes are very specific types. Categorys are strings.
        final NumberAxis yAxis = new NumberAxis();
        final Label l = new Label("");
        l.setStyle("-fx-font: 24 arial;");
        l.setTextFill(Color.AZURE);
        xAxis.setLabel("Years");
        yAxis.setLabel("Number of Events");
        //Charts are made up of individual pieces of Chart.Data. In this case, a piece of barData is a single bar on the graph.
        //Data is packaged into a series, which can be assigned custom colors or styling
        //After the series are created, 1 or more series are packaged into a single chart.
        ObservableList<BarChart.Series<String, Number>> bcData = FXCollections.observableArrayList();
        BarChart.Series<String, Number> se = new BarChart.Series<String, Number>();
        for (final YearEpoch ye : allYears) {
            se.getData().add(new BarChart.Data<String, Number>(String.valueOf(ye.year), ye.getNumFiles()));
        }
        bcData.add(se);

        //Note: 
        // BarChart.Data wraps the Java Nodes class. BUT, until a BarChart.Data gets added to an actual series, it's node is null, and you can perform no operations on it.
        // When the Data is added to a series(or a chart? I am unclear on where), a node is automaticaly generated for it, after which you can perform any of the operations it offers. 
        // In addtion, you are free to set the node to whatever you want. It wraps the most generic Node class.
        // But it is for this reason that the chart generating functions have two forloops. I do not believe they can be condensed into a single loop due to the nodes being null until 
        // an undetermined point in time. 
        BarChart<String, Number> bc = new BarChart<String, Number>(xAxis, yAxis, bcData);
        for (final BarChart.Data barData : bc.getData().get(0).getData()) { //.get(0) refers to the BarChart.Series class to work on. There is only one series in this graph, so get(0) is safe.
            barData.getNode().setScaleX(.5);

            final javafx.scene.Node barNode = barData.getNode();
            //hover listener
            barNode.addEventHandler(MouseEvent.MOUSE_ENTERED_TARGET, mouseEnteredListener);
            barNode.addEventHandler(MouseEvent.MOUSE_EXITED_TARGET, mouseExitedListener);

            //click listener
            barNode.addEventHandler(MouseEvent.MOUSE_CLICKED,
                    new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent e) {
                    if (e.getButton().equals(MouseButton.PRIMARY)) {
                        if (e.getClickCount() == 1) {
                            PlatformImpl.startup(new Runnable() {
                                @Override
                                public void run() {
                                    BarChart b = createMonthsWithDrill((YearEpoch) findYear(allYears, Integer.valueOf((String) barData.getXValue())));
                                    chart_Events = b;
                                    scroll_Events.setContent(chart_Events);
                                }
                            });

                        }
                    }
                }
            });
        }

        bc.autosize(); //Get an auto height
        bc.setPrefWidth(Width_Frame);  //but override the width
        bc.setLegendVisible(false); //The legend adds too much extra chart space, it's not necessary.
        return bc;
    }

    /*
     * Displays a chart with events from one year only, separated into 1-month chunks.
     * Always 12 per year, empty months are represented by no bar.
     */
    private BarChart createMonthsWithDrill(final YearEpoch ye) {

        final CategoryAxis xAxis = new CategoryAxis();
        final NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Month (" + ye.year + ")");
        yAxis.setLabel("Number of Events");
        ObservableList<BarChart.Series<String, Number>> bcData = FXCollections.observableArrayList();

        BarChart.Series<String, Number> se = new BarChart.Series<String, Number>();
        for (int monthNum = 0; monthNum < 12; ++monthNum) {
            String monthName = new DateFormatSymbols().getMonths()[monthNum];
            MonthEpoch month = ye.getMonth(monthNum);
            int numEvents = month == null ? 0 : month.getNumFiles();
            se.getData().add(new BarChart.Data<String, Number>(monthName, numEvents)); //Adding new barData at {X-pos, Y-Pos}
        }
        bcData.add(se);
        final BarChart<String, Number> bc = new BarChart<String, Number>(xAxis, yAxis, bcData);

        for (int i = 0; i < 12; i++) {
            for (final BarChart.Data barData : bc.getData().get(0).getData()) {
                //Note: 
                // All the charts of this package have a problem where when the chart gets below a certain pixel ratio, the barData stops drawing. The axes and the labels remain, 
                // But the actual chart barData is invisible, unclickable, and unrendered. To partially compensate for that, barData.getNode() can be manually scaled up to increase visibility.
                // Sometimes I've had it jacked up to as much as x2400 just to see a sliver of information.
                // But that doesn't work all the time. Adding it to a scrollpane and letting the user scroll up and down to view the chart is the other workaround. Both of these fixes suck.
                final javafx.scene.Node barNode = barData.getNode();
                barNode.setScaleX(.5);

                //hover listener
                barNode.addEventHandler(MouseEvent.MOUSE_ENTERED_TARGET, mouseEnteredListener);
                barNode.addEventHandler(MouseEvent.MOUSE_EXITED_TARGET, mouseExitedListener);

                //clicks
                barNode.addEventHandler(MouseEvent.MOUSE_PRESSED,
                        new EventHandler<MouseEvent>() {
                    @Override
                    public void handle(MouseEvent e) {
                        if (e.getButton().equals(MouseButton.PRIMARY)) {
                            if (e.getClickCount() == 1) {
                                PlatformImpl.startup(new Runnable() {
                                    @Override
                                    public void run() {
                                        chart_Events = createEventsByMonth(findMonth(ye.months, monthStringToInt((String) barData.getXValue())), ye);
                                        scroll_Events.setContent(chart_Events);
                                    }
                                });
                            }
                        }
                    }
                });
            }
        }

        bc.autosize();
        bc.setPrefWidth(Width_Frame);
        bc.setLegendVisible(false);
        stack_PrevCharts.push(bc);
        return bc;
    }


    /*
     * Displays a chart with events from one month only.
     * Up to 31 days per month, as low as 28 as determined by the specific MonthEpoch
     */
    private BarChart createEventsByMonth(final MonthEpoch me, final YearEpoch ye) {
        final CategoryAxis xAxis = new CategoryAxis();
        final NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Day of Month");
        yAxis.setLabel("Number of Events");
        ObservableList<BarChart.Data> bcData = makeObservableListByMonthAllDays(me, ye.getYear());
        BarChart.Series<String, Number> series = new BarChart.Series(bcData);
        series.setName(me.getMonthName() + " " + ye.getYear());


        ObservableList<BarChart.Series<String, Number>> ol = FXCollections.observableArrayList(series);

        final BarChart<String, Number> bc = new BarChart<String, Number>(xAxis, yAxis, ol);
        for (final BarChart.Data barData : bc.getData().get(0).getData()) {
            //data.getNode().setScaleX(2);

            final javafx.scene.Node barNode = barData.getNode();

            //hover listener
            barNode.addEventHandler(MouseEvent.MOUSE_ENTERED_TARGET, mouseEnteredListener);
            barNode.addEventHandler(MouseEvent.MOUSE_EXITED_TARGET, mouseExitedListener);

            barNode.addEventHandler(MouseEvent.MOUSE_PRESSED,
                    new EventHandler<MouseEvent>() {
                MonthEpoch myme = me;

                @Override
                public void handle(MouseEvent e) {
                    int day = (Integer.valueOf(((String) barData.getXValue()).split("-")[1]));
                    DayEpoch de = myme.getDay(day);
                    List<AbstractFile> afs = Collections.EMPTY_LIST;
                    if (de != null) {
                        afs = de.getEvents();
                    } else {
                        logger.log(Level.SEVERE, "There were no events for the clicked-on day.");
                    }
                    final FsContentRootNode d = new FsContentRootNode("Test Root", afs);
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            dataResult.setNode(d);
                        }
                    });

                    //set result viewer title path with the current date
                    String dateString = ye.getYear() + "-" + (1 + me.getMonthInt()) + "-" + +de.dayNum;
                    dataResult.setPath(dateString);
                }
            });
        }
        bc.autosize();
        bc.setPrefWidth(Width_Frame);
        return bc;
    }

    private static ObservableList<BarChart.Data> makeObservableListByMonthAllDays(final MonthEpoch me, int year) {
        ObservableList<BarChart.Data> bcData = FXCollections.observableArrayList();
        int totalDays = me.getTotalNumDays(year);
        for (int i = 1; i <= totalDays; ++i) {
            DayEpoch day = me.getDay(i);
            int numFiles = day == null ? 0 : day.getNumFiles();
            BarChart.Data d = new BarChart.Data(me.month + 1 + "-" + i, numFiles);
            d.setExtraValue(me);
            bcData.add(d);
        }
        return bcData;
    }

    /*
     * Section for Utility functions
     */
    /**
     *
     * @param mon The month to convert. Must be minimum 4 characters long
     * "February" and "Febr" are acceptable.
     * @return The integer value of the month. February = 1, July = 6
     */
    private static int monthStringToInt(String mon) {
        try {
            Date date = new SimpleDateFormat("MMMM", Locale.ENGLISH).parse(mon);
            Calendar cal = Calendar.getInstance();
            cal.setTime(date);
            return cal.get(Calendar.MONTH);
        } catch (ParseException ex) {
            logger.log(Level.WARNING, "Unable to convert string " + mon + " to integer", ex);
            return -1;
        }
    }

    /**
     * Used for finding the proper month in a list of available months
     *
     * @param lst The list of months to search through. It is assumed that the
     * desired match is in this list.
     * @param match The month, in integer format, to retrieve.
     * @return The month epoch as specified by match.
     */
    private static MonthEpoch findMonth(List<MonthEpoch> lst, int match) {
        for (MonthEpoch e : lst) {
            if (e.month == match) {
                return e;
            }
        }
        return null;
    }

    /**
     * Used for finding the proper year in a list of available years
     *
     * @param lst The list of years to search through. It is assumed that the
     * desired match is in this list.
     * @param match The year to retrieve.
     * @return The year epoch as specified by match.
     */
    private static YearEpoch findYear(List<YearEpoch> lst, int match) {
        for (YearEpoch e : lst) {
            if (e.year == match) {
                return e;
            }
        }
        return null;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        String prop = evt.getPropertyName();
        if (prop.equals(Case.CASE_ADD_IMAGE)) {
            if (jf != null && !jf.isVisible()) {
                // change the lastObjectId to trigger a reparse of mactime barData
                ++lastObjectId;
                return;
            }

            int answer = JOptionPane.showConfirmDialog(jf, "Timeline is out of date. Would you like to regenerate it?", "Select an option", JOptionPane.YES_NO_OPTION);
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }

            clearMactimeData();

            // call performAction as if the user selected 'Make Timeline' from the menu
            performAction();
        } else if (prop.equals(Case.CASE_CURRENT_CASE)) {
            if (jf != null && jf.isVisible()) {
                jf.dispose();
                jf = null;
            }

            data = null;
        }
    }

    private void clearMactimeData() {
        // get rid of the old barData
        data = null;

        // get rid of the mactime file
        java.io.File mactimeFile = new java.io.File(moduleDir, mactimeFileName);
        mactimeFile.delete();

        // close the jframe
        if (jf != null) {
            jf.setVisible(false);
            jf.dispose();
            jf = null;
        }

        // remove ourself as change listener on Case
        Case.removePropertyChangeListener(this);
        listeningToAddImage = false;

    }

    /*
     * The backbone of the timeline functionality, years are split into months, months into days, and days contain the events of that given day.
     * All of those are Epochs.
     */
    abstract class Epoch {

        abstract public int getNumFiles();
    }

    private class YearEpoch extends Epoch {

        private int year;
        private List<MonthEpoch> months = new ArrayList<>();

        YearEpoch(int year) {
            this.year = year;
        }

        public int getYear() {
            return year;
        }

        @Override
        public int getNumFiles() {
            int size = 0;
            for (MonthEpoch me : months) {
                size += me.getNumFiles();
            }
            return size;
        }

        public MonthEpoch getMonth(int monthNum) {
            MonthEpoch month = null;
            for (MonthEpoch me : months) {
                if (me.getMonthInt() == monthNum) {
                    month = me;
                    break;
                }
            }
            return month;
        }

        public void add(AbstractFile af, int month, int day) {
            // see if this month is in the list
            MonthEpoch monthEpoch = null;
            for (MonthEpoch me : months) {
                if (me.getMonthInt() == month) {
                    monthEpoch = me;
                    break;
                }
            }

            if (monthEpoch == null) {
                monthEpoch = new MonthEpoch(month);
                months.add(monthEpoch);
            }

            // add the file the the MonthEpoch object
            monthEpoch.add(af, day);
        }
    }

    private class MonthEpoch extends Epoch {

        private int month; //Zero-indexed: June = 5, August = 7, etc
        private List<DayEpoch> days = new ArrayList<>(); //List of DayEpochs in this month, max 31

        MonthEpoch(int month) {
            this.month = month;
        }

        public int getMonthInt() {
            return month;
        }

        public int getTotalNumDays(int year) {
            Calendar cal = Calendar.getInstance();
            cal.set(year, month, 1);
            return cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        }

        @Override
        public int getNumFiles() {
            int numFiles = 0;
            for (DayEpoch de : days) {
                numFiles += de.getNumFiles();
            }
            return numFiles;
        }

        public DayEpoch getDay(int dayNum) {
            DayEpoch de = null;
            for (DayEpoch d : days) {
                if (d.dayNum == dayNum) {
                    de = d;
                    break;
                }
            }
            return de;
        }

        public void add(AbstractFile af, int day) {
            DayEpoch dayEpoch = null;
            for (DayEpoch de : days) {
                if (de.getDayInt() == day) {
                    dayEpoch = de;
                    break;
                }
            }

            if (dayEpoch == null) {
                dayEpoch = new DayEpoch(day);
                days.add(dayEpoch);
            }

            dayEpoch.add(af);
        }

        /**
         * Returns the month's name in String format, e.g., September, July,
         */
        String getMonthName() {
            return new DateFormatSymbols().getMonths()[month];
        }

        /**
         * @return the list of days in this month
         */
        List<DayEpoch> getDays() {
            return this.days;
        }
    }

    private class DayEpoch extends Epoch {

        private List<AbstractFile> files = new ArrayList<>();
        int dayNum = 0; //Day of the month this Epoch represents, 1 indexed: 28=28.

        DayEpoch(int dayOfMonth) {
            this.dayNum = dayOfMonth;
        }

        public int getDayInt() {
            return dayNum;
        }

        @Override
        public int getNumFiles() {
            return files.size();
        }

        public void add(AbstractFile af) {
            files.add(af);
        }

        List<AbstractFile> getEvents() {
            return this.files;
        }
    }

    // The node factories used to make lists of files to send to the result viewer
    private class FsContentNodeChildFactory extends ChildFactory<AbstractFile> {

        List<AbstractFile> l;

        FsContentNodeChildFactory(List<AbstractFile> l) {
            this.l = l;
        }

        @Override
        protected boolean createKeys(List<AbstractFile> list) {
            list.addAll(l);
            return true;
        }

        @Override
        protected Node createNodeForKey(AbstractFile file) {
            Node wrapped;
            if (file.isDir()) {
                wrapped = new DirectoryNode((Directory) file, false);
            } else {
                wrapped = new FileNode((File) file, false);
            }
            return new FilterNodeLeaf(wrapped);
        }
    }

    private class FsContentRootNode extends DisplayableItemNode {

        FsContentRootNode(String NAME, List<AbstractFile> l) {
            super(Children.create(new FsContentNodeChildFactory(l), true));
            super.setName(NAME);
            super.setDisplayName(NAME);
        }

        @Override
        public DisplayableItemNode.TYPE getDisplayableItemNodeType() {
            return DisplayableItemNode.TYPE.CONTENT;
        }

        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> v) {
            return null;
        }
    }

    private List<YearEpoch> parseMacTime(java.io.File f) {
        List<YearEpoch> years = new ArrayList<>();
        Scanner scan;
        try {
            scan = new Scanner(new FileInputStream(f));
        } catch (FileNotFoundException ex) {
            logger.log(Level.SEVERE, "Error: could not find mactime file.", ex);
            return years;
        }
        scan.useDelimiter(",");
        scan.nextLine();   // skip the header line
        SleuthkitCase skCase = Case.getCurrentCase().getSleuthkitCase();

        int prevYear = -1;
        YearEpoch ye = null;
        while (scan.hasNextLine()) {
            String[] s = scan.nextLine().split(","); //1999-02-08T11:08:08Z, 78706, m..b, rrwxrwxrwx, 0, 0, 8355, /img...
            String[] datetime = s[0].split("T"); //{1999-02-08, 11:08:08Z}
            String[] date = datetime[0].split("-"); // {1999, 02, 08}
            int year = Integer.valueOf(date[0]);
            int month = Integer.valueOf(date[1]) - 1; //Months are zero indexed: 1 = February, 6 = July, 11 = December
            int day = Integer.valueOf(date[2]); //Days are 1 indexed
            long ObjId = Long.valueOf(s[4]);

            // when the year changes, create and add a new YearEpoch object to the list
            if (year != prevYear) {
                ye = new YearEpoch(year);
                years.add(ye);
                prevYear = year;
            }

            // create and add the file
            AbstractFile file;
            try {
                file = skCase.getAbstractFileById(ObjId);
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Could not find a file with ID " + ObjId, ex);
                continue;
            }
            if (ye != null) {
                ye.add(file, month, day);
            }
        }

        scan.close();

        return years;
    }

    /**
     * Crate a body file and return its path or null if error
     *
     * @return absolute path string or null if error
     */
    private String makeBodyFile() {
        // Setup timestamp
        DateFormat dateFormat = new SimpleDateFormat("MM-dd-yyyy-HH-mm-ss");
        Date date = new Date();
        String datenotime = dateFormat.format(date);

        final Case currentCase = Case.getCurrentCase();
        final SleuthkitCase skCase = currentCase.getSleuthkitCase();

        // Get report path
        String bodyFilePath = moduleDir.getAbsolutePath()
                + java.io.File.separator + currentCase.getName() + "-" + datenotime + ".txt";

        // Run query to get all files
        String filesAndDirs = "type = '" + TskData.TSK_DB_FILES_TYPE_ENUM.FS.getFileType() + "' "
                + "AND name != '.' "
                + "AND name != '..'";
        List<FsContent> fs = null;
        try {
            fs = skCase.findFilesWhere(filesAndDirs);
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error querying image files to make a body file: " + bodyFilePath, ex);
            return null;
        }

        // Loop files and write info to report
        FileWriter fileWriter = null;
        try {
            fileWriter = new FileWriter(bodyFilePath, true);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error creating output stream to write body file to: " + bodyFilePath, ex);
            return null;
        }

        BufferedWriter out = null;
        try {
            out = new BufferedWriter(fileWriter);
            for (FsContent file : fs) {
                // try {
                // MD5|name|inode|mode_as_string|ObjId|GID|size|atime|mtime|ctime|crtime
                if (file.getMd5Hash() != null) {
                    out.write(file.getMd5Hash());
                }
                out.write("|");
                String path = null;
                try {
                    path = file.getUniquePath();
                } catch (TskCoreException e) {
                    logger.log(Level.SEVERE, "Failed to get the unique path of: " + file + " and writing body file.", e);
                    return null;
                }

                out.write(path);

                out.write("|");
                out.write(Long.toString(file.getMetaAddr()));
                out.write("|");
                String modeString = file.getModesAsString();
                if (modeString != null) {
                    out.write(modeString);
                }
                out.write("|");
                out.write(Long.toString(file.getId()));
                out.write("|");
                out.write(Long.toString(file.getGid()));
                out.write("|");
                out.write(Long.toString(file.getSize()));
                out.write("|");
                out.write(Long.toString(file.getAtime()));
                out.write("|");
                out.write(Long.toString(file.getMtime()));
                out.write("|");
                out.write(Long.toString(file.getCtime()));
                out.write("|");
                out.write(Long.toString(file.getCrtime()));
                out.write("\n");
            }
        } catch (IOException ex) {
            logger.log(Level.WARNING, "Error while trying to write data to the body file.", ex);
            return null;
        } finally {
            if (out != null) {
                try {
                    out.flush();
                    out.close();
                } catch (IOException ex1) {
                    logger.log(Level.WARNING, "Could not flush and/or close body file.", ex1);
                }
            }
        }


        return bodyFilePath;
    }

    private String makeMacTime(String pathToBodyFile) {
        String macpath = "";
        final String machome = macRoot.getAbsolutePath();
        pathToBodyFile = PlatformUtil.getOSFilePath(pathToBodyFile);
        if (PlatformUtil.isWindowsOS()) {
            macpath = machome + java.io.File.separator + "mactime.exe";
            macpath = PlatformUtil.getOSFilePath(macpath);
        } else {
            macpath = "perl " + machome + java.io.File.separator + "mactime.pl";
        }
        String macfile = moduleDir.getAbsolutePath() + java.io.File.separator + mactimeFileName;
        macfile = PlatformUtil.getOSFilePath(macfile);
        String command = macpath + " -b " + pathToBodyFile + " -d " + " -y " + "> " + macfile;

        try {
            JavaSystemCaller.Exec.execute("\"" + command + "\"");
        } catch (InterruptedException ie) {
            logger.log(Level.WARNING, "Mactime process was interrupted by user", ie);
            return null;
        } catch (IOException ioe) {
            logger.log(Level.SEVERE, "Could not create mactime file, encountered error ", ioe);
            return null;
        }
        return macfile;
    }

    @Override
    public boolean isEnabled() {
        return Case.isCaseOpen();
    }

    @Override
    public void performAction() {
        if (!Case.existsCurrentCase()) {
            return;
        }

        final Case currentCase = Case.getCurrentCase();

        try {
            if (currentCase.getImages().isEmpty()) {
                logger.log(Level.INFO, "Error creating timeline, there are no images to parse");
            } else {

                if (IngestManager.getDefault().isIngestRunning()) {
                    int answer = JOptionPane.showConfirmDialog(new JFrame(),
                            "You are trying to generate a timeline before "
                            + "ingest has been completed. The timeline may be "
                            + "incomplete. Do you want to continue?", "Timeline",
                            JOptionPane.YES_NO_OPTION);
                    if (answer != JOptionPane.YES_OPTION) {
                        return;
                    }
                }

                logger.log(Level.INFO, "Beginning generation of timeline");

                // if the timeline window is already open, do nothing
                if (jf != null && jf.isVisible()) {
                    return;
                }

                Platform.setImplicitExit(false);

                // listen for case changes (specifically images being added).
                if (Case.isCaseOpen() && !listeningToAddImage) {
                    Case.addPropertyChangeListener(this);
                    listeningToAddImage = true;
                }

                // create the modal progressDialog
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog = new TimelineProgressDialog(jf, true);
                        progressDialog.setVisible(true);
                    }
                });

                // initialize mactimeFileName
                mactimeFileName = currentCase.getName() + "-MACTIME.txt";

                // see if barData has been added to the database since the last
                // time timeline ran
                long objId = currentCase.getSleuthkitCase().getLastObjectId();
                if (objId != lastObjectId && lastObjectId != -1) {
                    clearMactimeData();
                }
                lastObjectId = objId;

                customizeSwing();
                customize();
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error when generating timeline, ", ex);
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "Unexpected error when generating timeline, ", ex);
        }
    }

    @Override
    public String getName() {
        return "Make Timeline (Beta)";
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public boolean asynchronous() {
        return false;
    }
}
