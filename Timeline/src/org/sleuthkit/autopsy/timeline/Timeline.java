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

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
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
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionReferences;
import org.openide.awt.ActionRegistration;
import org.openide.modules.InstalledFileLocator;
import org.openide.modules.ModuleInstall;
import org.openide.nodes.Children;
import org.openide.nodes.Node;
import org.openide.util.HelpCtx;
import org.openide.util.NbBundle;
import org.openide.util.actions.CallableSystemAction;
import org.openide.util.actions.Presenter;
import org.openide.util.lookup.Lookups;
import org.openide.windows.WindowManager;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.core.Installer;
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
import org.sleuthkit.autopsy.coreutils.ExecUtil;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

@ActionID(category = "Tools", id = "org.sleuthkit.autopsy.timeline.Timeline")
@ActionRegistration(displayName = "#CTL_MakeTimeline", lazy = false)
@ActionReferences(value = {
    @ActionReference(path = "Menu/Tools", position = 100)})
@NbBundle.Messages(value = "CTL_TimelineView=Generate Timeline")
/**
 * The Timeline Action entry point. Collects data and pushes data to javafx
 * widgets
 *
 */
public class Timeline extends CallableSystemAction implements Presenter.Toolbar, PropertyChangeListener {

    private static final Logger logger = Logger.getLogger(Timeline.class.getName());
    private final java.io.File macRoot = InstalledFileLocator.getDefault().locate("mactime", Timeline.class.getPackage().getName(), false);
    private TimelineFrame mainFrame;          //frame for holding all the elements
    private Group fxGroupCharts; //Orders the charts
    private Scene fxSceneCharts; //Displays the charts
    private HBox fxHBoxCharts;      //Holds the navigation buttons in horiztonal fashion. 
    private VBox fxVBox;        //Holds the JavaFX Elements in vertical fashion. 
    private JFXPanel fxPanelCharts;  //FX panel to hold the group
    private BarChart<String, Number> fxChartEvents;      //Yearly/Monthly events - Bar chart
    private ScrollPane fxScrollEvents;  //Scroll Panes for dealing with oversized an oversized chart
    private static final int FRAME_HEIGHT = 700; //Sizing constants
    private static final int FRAME_WIDTH = 1200;
    private Button fxZoomOutButton;  //Navigation buttons
    private ComboBox<String> fxDropdownSelectYears; //Dropdown box for selecting years. Useful when the charts' scale means some years are unclickable, despite having events.
    private final Stack<BarChart<String, Number>> fxStackPrevCharts = new Stack<BarChart<String, Number>>();  //Stack for storing drill-up information.
    private BarChart<String, Number> fxChartTopLevel; //the topmost chart, used for resetting to default view.
    private DataResultPanel dataResultPanel;
    private DataContentPanel dataContentPanel;
    private ProgressHandle progress;
    private java.io.File moduleDir;
    private String mactimeFileName;
    private List<YearEpoch> data;
    private boolean listeningToAddImage = false;
    private long lastObjectId = -1;
    private TimelineProgressDialog progressDialog;
    private EventHandler<MouseEvent> fxMouseEnteredListener;
    private EventHandler<MouseEvent> fxMouseExitedListener;
    private SleuthkitCase skCase;
    private boolean fxInited = false;

    public Timeline() {
        super();

        fxInited = Installer.isJavaFxInited();

    }

    //Swing components and JavafX components don't play super well together
    //Swing components need to be initialized first, in the swing specific thread
    //Next, the javafx components may be initialized.
    private void customize() {

        //listeners
        fxMouseEnteredListener = new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent e) {
                fxPanelCharts.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }
        };
        fxMouseExitedListener = new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent e) {
                fxPanelCharts.setCursor(null);
            }
        };

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                //Making the main frame *

                mainFrame = new TimelineFrame();
                mainFrame.setFrameName(Case.getCurrentCase().getName() + " - Autopsy Timeline (Beta)");

                //use the same icon on jframe as main application
                mainFrame.setIconImage(WindowManager.getDefault().getMainWindow().getIconImage());
                mainFrame.setFrameSize(new Dimension(FRAME_WIDTH, FRAME_HEIGHT)); //(Width, Height)


                dataContentPanel = DataContentPanel.createInstance();
                //dataContentPanel.setAlignmentX(Component.RIGHT_ALIGNMENT);
                //dataContentPanel.setPreferredSize(new Dimension(FRAME_WIDTH, (int) (FRAME_HEIGHT * 0.4)));

                dataResultPanel = DataResultPanel.createInstance("Timeline Results", "", Node.EMPTY, 0, dataContentPanel);
                dataResultPanel.setContentViewer(dataContentPanel);
                //dataResultPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
                //dataResultPanel.setPreferredSize(new Dimension((int)(FRAME_WIDTH * 0.5), (int) (FRAME_HEIGHT * 0.5)));
                logger.log(Level.INFO, "Successfully created viewers");

                mainFrame.setBottomLeftPanel(dataResultPanel);
                mainFrame.setBottomRightPanel(dataContentPanel);

                runJavaFxThread();
            }
        });


    }

    private void runJavaFxThread() {
        //JavaFX thread
        //JavaFX components MUST be run in the JavaFX thread, otherwise massive amounts of exceptions will be thrown and caught. Liable to freeze up and crash.
        //Components can be declared whenever, but initialization and manipulation must take place here.
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                try {
                    // start the progress bar
                    progress = ProgressHandleFactory.createHandle("Creating timeline . . .");
                    progress.start();

                    fxChartEvents = null; //important to reset old data
                    fxPanelCharts = new JFXPanel();
                    fxGroupCharts = new Group();
                    fxSceneCharts = new Scene(fxGroupCharts, FRAME_WIDTH, FRAME_HEIGHT * 0.6); //Width, Height
                    fxVBox = new VBox(5);
                    fxVBox.setAlignment(Pos.BOTTOM_CENTER);
                    fxHBoxCharts = new HBox(10);
                    fxHBoxCharts.setAlignment(Pos.BOTTOM_CENTER);

                    //Initializing default values for the scroll pane
                    fxScrollEvents = new ScrollPane();
                    fxScrollEvents.setPrefSize(FRAME_WIDTH, FRAME_HEIGHT * 0.6); //Width, Height
                    fxScrollEvents.setContent(null); //Needs some content, otherwise it crashes

                    // set up moduleDir
                    moduleDir = new java.io.File(Case.getCurrentCase().getModulesOutputDirAbsPath() + java.io.File.separator + "timeline");
                    if (!moduleDir.exists()) {
                        moduleDir.mkdir();
                    }

                    int currentProgress = 0;
                    java.io.File mactimeFile = new java.io.File(moduleDir, mactimeFileName);
                    if (!mactimeFile.exists()) {
                        progressDialog.setProgressTotal(3); //total 3 units
                        logger.log(Level.INFO, "Creating body file");
                        progressDialog.updateProgressBar("Generating Bodyfile");
                        String bodyFilePath = makeBodyFile();
                        progressDialog.updateProgressBar(++currentProgress);
                        logger.log(Level.INFO, "Creating mactime file: " + mactimeFile.getAbsolutePath());
                        progressDialog.updateProgressBar("Generating Mactime");
                        makeMacTime(bodyFilePath);
                        progressDialog.updateProgressBar(++currentProgress);
                        data = null;
                    } else {
                        progressDialog.setProgressTotal(1); //total 1 units
                        logger.log(Level.INFO, "Mactime file already exists; parsing that: " + mactimeFile.getAbsolutePath());
                    }


                    progressDialog.updateProgressBar("Parsing Mactime");
                    if (data == null) {
                        logger.log(Level.INFO, "Parsing mactime file: " + mactimeFile.getAbsolutePath());
                        data = parseMacTime(mactimeFile); //The sum total of the mactime parsing.  YearEpochs contain everything you need to make a timeline.
                    }
                    progressDialog.updateProgressBar(++currentProgress);

                    //Making a dropdown box to select years.
                    List<String> lsi = new ArrayList<String>();  //List is in the format of {Year : Number of Events}, used for selecting from the dropdown.
                    for (YearEpoch ye : data) {
                        lsi.add(ye.year + " : " + ye.getNumFiles());
                    }
                    ObservableList<String> listSelect = FXCollections.observableArrayList(lsi);
                    fxDropdownSelectYears = new ComboBox<String>(listSelect);

                    //Buttons for navigating up and down the timeline
                    fxZoomOutButton = new Button("Zoom Out");
                    fxZoomOutButton.setOnAction(new EventHandler<ActionEvent>() {
                        @Override
                        public void handle(ActionEvent e) {
                            BarChart<String, Number> bc;
                            if (fxStackPrevCharts.size() == 0) {
                                bc = fxChartTopLevel;
                            } else {
                                bc = fxStackPrevCharts.pop();
                            }
                            fxChartEvents = bc;
                            fxScrollEvents.setContent(fxChartEvents);
                        }
                    });

                    fxDropdownSelectYears.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
                        @Override
                        public void changed(ObservableValue<? extends String> ov, String t, String t1) {
                            if (fxDropdownSelectYears.getValue() != null) {
                                mainFrame.setTopComponentCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                                try {
                                    fxChartEvents = createMonthsWithDrill(findYear(data, Integer.valueOf(fxDropdownSelectYears.getValue().split(" ")[0])));
                                    fxScrollEvents.setContent(fxChartEvents);
                                } finally {
                                    mainFrame.setTopComponentCursor(null);
                                }
                            }
                        }
                    });

                    //Adding things to the V and H boxes. 
                    //hBox_Charts stores the pseudo menu bar at the top of the timeline. |Zoom Out|View Year: [Select Year]|►|
                    fxHBoxCharts.getChildren().addAll(fxZoomOutButton, new Label("Go To:"), fxDropdownSelectYears);
                    fxVBox.getChildren().addAll(fxHBoxCharts, fxScrollEvents); //FxBox_V holds things in a visual stack. 
                    fxGroupCharts.getChildren().add(fxVBox); //Adding the FxBox to the group. Groups make things easier to manipulate without having to update a hundred things every change.
                    fxPanelCharts.setScene(fxSceneCharts);


                    fxPanelCharts.setAlignmentX(Component.LEFT_ALIGNMENT);

                    fxChartTopLevel = createYearChartWithDrill(data);
                    fxChartEvents = fxChartTopLevel;
                    fxScrollEvents.setContent(fxChartEvents);

                    EventQueue.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            mainFrame.setTopPanel(fxPanelCharts);
                            dataResultPanel.open();
                            //mainFrame.pack();
                            mainFrame.setVisible(true);
                        }
                    });
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
    private BarChart<String, Number> createYearChartWithDrill(final List<YearEpoch> allYears) {
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
        if (allYears != null) {
            for (final YearEpoch ye : allYears) {
                se.getData().add(new BarChart.Data<String, Number>(String.valueOf(ye.year), ye.getNumFiles()));
            }
        }
        bcData.add(se);


        //Note: 
        // BarChart.Data wraps the Java Nodes class. BUT, until a BarChart.Data gets added to an actual series, it's node is null, and you can perform no operations on it.
        // When the Data is added to a series(or a chart? I am unclear on where), a node is automaticaly generated for it, after which you can perform any of the operations it offers. 
        // In addtion, you are free to set the node to whatever you want. It wraps the most generic Node class.
        // But it is for this reason that the chart generating functions have two forloops. I do not believe they can be condensed into a single loop due to the nodes being null until 
        // an undetermined point in time. 
        BarChart<String, Number> bc = new BarChart<String, Number>(xAxis, yAxis, bcData);
        for (final BarChart.Data<String, Number> barData : bc.getData().get(0).getData()) { //.get(0) refers to the BarChart.Series class to work on. There is only one series in this graph, so get(0) is safe.
            barData.getNode().setScaleX(.5);

            final javafx.scene.Node barNode = barData.getNode();
            //hover listener
            barNode.addEventHandler(MouseEvent.MOUSE_ENTERED_TARGET, fxMouseEnteredListener);
            barNode.addEventHandler(MouseEvent.MOUSE_EXITED_TARGET, fxMouseExitedListener);

            //click listener
            barNode.addEventHandler(MouseEvent.MOUSE_CLICKED,
                    new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent e) {
                    if (e.getButton().equals(MouseButton.PRIMARY)) {
                        if (e.getClickCount() == 1) {
                            Platform.runLater(new Runnable() {
                                @Override
                                public void run() {
                                    BarChart<String, Number> b =
                                            createMonthsWithDrill(findYear(allYears, Integer.valueOf(barData.getXValue())));
                                    fxChartEvents = b;
                                    fxScrollEvents.setContent(fxChartEvents);
                                }
                            });

                        }
                    }
                }
            });
        }

        bc.autosize(); //Get an auto height
        bc.setPrefWidth(FRAME_WIDTH);  //but override the width
        bc.setLegendVisible(false); //The legend adds too much extra chart space, it's not necessary.
        return bc;
    }

    /*
     * Displays a chart with events from one year only, separated into 1-month chunks.
     * Always 12 per year, empty months are represented by no bar.
     */
    private BarChart<String, Number> createMonthsWithDrill(final YearEpoch ye) {

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
            for (final BarChart.Data<String, Number> barData : bc.getData().get(0).getData()) {
                //Note: 
                // All the charts of this package have a problem where when the chart gets below a certain pixel ratio, the barData stops drawing. The axes and the labels remain, 
                // But the actual chart barData is invisible, unclickable, and unrendered. To partially compensate for that, barData.getNode() can be manually scaled up to increase visibility.
                // Sometimes I've had it jacked up to as much as x2400 just to see a sliver of information.
                // But that doesn't work all the time. Adding it to a scrollpane and letting the user scroll up and down to view the chart is the other workaround. Both of these fixes suck.
                final javafx.scene.Node barNode = barData.getNode();
                barNode.setScaleX(.5);

                //hover listener
                barNode.addEventHandler(MouseEvent.MOUSE_ENTERED_TARGET, fxMouseEnteredListener);
                barNode.addEventHandler(MouseEvent.MOUSE_EXITED_TARGET, fxMouseExitedListener);

                //clicks
                barNode.addEventHandler(MouseEvent.MOUSE_PRESSED,
                        new EventHandler<MouseEvent>() {
                    @Override
                    public void handle(MouseEvent e) {
                        if (e.getButton().equals(MouseButton.PRIMARY)) {
                            if (e.getClickCount() == 1) {
                                Platform.runLater(new Runnable() {
                                    @Override
                                    public void run() {
                                        fxChartEvents = createEventsByMonth(findMonth(ye.months, monthStringToInt(barData.getXValue())), ye);
                                        fxScrollEvents.setContent(fxChartEvents);
                                    }
                                });
                            }
                        }
                    }
                });
            }
        }

        bc.autosize();
        bc.setPrefWidth(FRAME_WIDTH);
        bc.setLegendVisible(false);
        fxStackPrevCharts.push(bc);
        return bc;
    }


    /*
     * Displays a chart with events from one month only.
     * Up to 31 days per month, as low as 28 as determined by the specific MonthEpoch
     */
    private BarChart<String, Number> createEventsByMonth(final MonthEpoch me, final YearEpoch ye) {
        final CategoryAxis xAxis = new CategoryAxis();
        final NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Day of Month");
        yAxis.setLabel("Number of Events");
        ObservableList<BarChart.Data<String, Number>> bcData = makeObservableListByMonthAllDays(me, ye.getYear());
        BarChart.Series<String, Number> series = new BarChart.Series<String, Number>(bcData);
        series.setName(me.getMonthName() + " " + ye.getYear());


        ObservableList<BarChart.Series<String, Number>> ol =
                FXCollections.<BarChart.Series<String, Number>>observableArrayList(series);

        final BarChart<String, Number> bc = new BarChart<String, Number>(xAxis, yAxis, ol);
        for (final BarChart.Data<String, Number> barData : bc.getData().get(0).getData()) {
            //data.getNode().setScaleX(2);

            final javafx.scene.Node barNode = barData.getNode();

            //hover listener
            barNode.addEventHandler(MouseEvent.MOUSE_ENTERED_TARGET, fxMouseEnteredListener);
            barNode.addEventHandler(MouseEvent.MOUSE_EXITED_TARGET, fxMouseExitedListener);

            barNode.addEventHandler(MouseEvent.MOUSE_PRESSED,
                    new EventHandler<MouseEvent>() {
                MonthEpoch myme = me;

                @Override
                public void handle(MouseEvent e) {
                     SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            //reset the view and free the current nodes before loading new ones
                            final FileRootNode d = new FileRootNode("Empty Root", new ArrayList<Long>());
                            dataResultPanel.setNode(d);
                            dataResultPanel.setPath("Loading...");
                        }
                    });
                    final int day = (Integer.valueOf((barData.getXValue()).split("-")[1]));
                    final DayEpoch de = myme.getDay(day);
                    final List<Long> afs;
                    if (de != null) {
                        afs = de.getEvents();
                    } else {
                        logger.log(Level.SEVERE, "There were no events for the clicked-on day: " + day);
                        return;
                    }

                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            final FileRootNode d = new FileRootNode("Root", afs);
                            dataResultPanel.setNode(d);
                            //set result viewer title path with the current date
                            String dateString = ye.getYear() + "-" + (1 + me.getMonthInt()) + "-" + +de.dayNum;
                            dataResultPanel.setPath(dateString);
                        }
                    });


                }
            });
        }
        bc.autosize();
        bc.setPrefWidth(FRAME_WIDTH);
        return bc;
    }

    private static ObservableList<BarChart.Data<String, Number>> makeObservableListByMonthAllDays(final MonthEpoch me, int year) {
        ObservableList<BarChart.Data<String, Number>> bcData = FXCollections.observableArrayList();
        int totalDays = me.getTotalNumDays(year);
        for (int i = 1; i <= totalDays; ++i) {
            DayEpoch day = me.getDay(i);
            int numFiles = day == null ? 0 : day.getNumFiles();
            BarChart.Data<String, Number> d = new BarChart.Data<String, Number>(me.month + 1 + "-" + i, numFiles);
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
        if (prop.equals(Case.Events.DATA_SOURCE_ADDED.toString())) {
            if (mainFrame != null && !mainFrame.isVisible()) {
                // change the lastObjectId to trigger a reparse of mactime barData
                ++lastObjectId;
                return;
            }

            int answer = JOptionPane.showConfirmDialog(mainFrame, "Timeline is out of date. Would you like to regenerate it?", "Select an option", JOptionPane.YES_NO_OPTION);
            if (answer != JOptionPane.YES_OPTION) {
                return;
            }

            clearMactimeData();

            // call performAction as if the user selected 'Make Timeline' from the menu
            performAction();
        } else if (prop.equals(Case.Events.CURRENT_CASE.toString())) {
            if (mainFrame != null && mainFrame.isVisible()) {
                mainFrame.dispose();
                mainFrame = null;
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
        if (mainFrame != null) {
            mainFrame.setVisible(false);
            mainFrame.dispose();
            mainFrame = null;
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

        public void add(long fileId, int month, int day) {
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
            monthEpoch.add(fileId, day);
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

        public void add(long fileId, int day) {
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

            dayEpoch.add(fileId);
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

        private final List<Long> fileIds = new ArrayList<>();
        int dayNum = 0; //Day of the month this Epoch represents, 1 indexed: 28=28.

        DayEpoch(int dayOfMonth) {
            this.dayNum = dayOfMonth;
        }

        public int getDayInt() {
            return dayNum;
        }

        @Override
        public int getNumFiles() {
            return fileIds.size();
        }

        public void add(long fileId) {
            fileIds.add(fileId);
        }

        List<Long> getEvents() {
            return this.fileIds;
        }
    }

    // The node factories used to make lists of files to send to the result viewer
    // using the lazy loading (rather than background) loading option to facilitate
    // loading a huge number of nodes for the given day
    private class FileNodeChildFactory extends Children.Keys<Long> {

        private List<Long> fileIds;

        FileNodeChildFactory(List<Long> fileIds) {
            super(true);
            this.fileIds = fileIds;
        }

        @Override
        protected void addNotify() {
            super.addNotify();
            setKeys(fileIds);
        }

        @Override
        protected void removeNotify() {
            super.removeNotify();
            setKeys(new ArrayList<Long>());
        }

        @Override
        protected Node[] createNodes(Long t) {
            return new Node[]{createNodeForKey(t)};
        }

        //  @Override
        //  protected boolean createKeys(List<Long> list) {
        //     list.addAll(fileIds);
        //     return true;
        //  }
        //@Override
        protected Node createNodeForKey(Long fileId) {
            AbstractFile af = null;
            try {
                af = skCase.getAbstractFileById(fileId);
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error getting file by id and creating a node in Timeline: " + fileId, ex);
                //no node will be shown for this object
                return null;
            }

            Node wrapped;
            if (af.isDir()) {
                wrapped = new DirectoryNode(af, false);
            } else {
                wrapped = new FileNode(af, false);
            }
            return new FilterNodeLeaf(wrapped);
        }
    }

    private class FileRootNode extends DisplayableItemNode {

        FileRootNode(String NAME, List<Long> fileIds) {
            //super(Children.create(new FileNodeChildFactory(fileIds), true));
            super(new FileNodeChildFactory(fileIds), Lookups.singleton(fileIds));
            super.setName(NAME);
            super.setDisplayName(NAME);
        }

        @Override
        public boolean isLeafTypeNode() {
            return false;
        }
        
        @Override
        public <T> T accept(DisplayableItemNodeVisitor<T> v) {
            return null;
        }
    }

    /**
     * Parse the output of mactime to break the results in to day-sized chunks (in GMT)
     * @param f handle to mactime csv output
     * @return 
     */
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

        int prevYear = -1;
        YearEpoch ye = null;
        while (scan.hasNextLine()) {
            String[] s = scan.nextLine().split(","); //1999-02-08T11:08:08Z, 78706, m..b, rrwxrwxrwx, 0, 0, 8355, /img...
            
            // break the date into mon, day and year: Note that the ISO times are in GMT
            String[] datetime = s[0].split("T"); //{1999-02-08, 11:08:08Z}
            String[] date = datetime[0].split("-"); // {1999, 02, 08}
            int year = Integer.valueOf(date[0]);
            int month = Integer.valueOf(date[1]) - 1; //Months are zero indexed: 1 = February, 6 = July, 11 = December
            int day = Integer.valueOf(date[2]); //Days are 1 indexed
            
            // get the object id out of the modified outpu
            long ObjId = Long.valueOf(s[4]);

            // when the year changes, create and add a new YearEpoch object to the list
            if (year != prevYear) {
                ye = new YearEpoch(year);
                years.add(ye);
                prevYear = year;
            }

            // save the object id along with the day
            if (ye != null) {
                ye.add(ObjId, month, day);
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

        // Get report path
        String bodyFilePath = moduleDir.getAbsolutePath()
                + java.io.File.separator + currentCase.getName() + "-" + datenotime + ".txt";

        // Run query to get all files
        final String filesAndDirs = "name != '.' "
                + "AND name != '..'";
        List<Long> fileIds = null;
        try {
            fileIds = skCase.findAllFileIdsWhere(filesAndDirs);
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
            for (long fileId : fileIds) {
                AbstractFile file = skCase.getAbstractFileById(fileId);
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
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Error querying file by id", ex);
            return null;

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

    /**
     * Run mactime on the given body file.  Generates CSV file with ISO dates (in GMT) 
     * @param pathToBodyFile
     * @return Path to output file. 
     */
    private String makeMacTime(String pathToBodyFile) {
        String cmdpath = "";
        String macpath = "";
        String[] mactimeArgs;
        final String machome = macRoot.getAbsolutePath();
        pathToBodyFile = PlatformUtil.getOSFilePath(pathToBodyFile);
        if (PlatformUtil.isWindowsOS()) {
            macpath = machome + java.io.File.separator + "mactime.exe";
            cmdpath = PlatformUtil.getOSFilePath(macpath);
            mactimeArgs = new String[]{"-b", pathToBodyFile, "-d", "-y"};
        } else {
            cmdpath = "perl";
            macpath = machome + java.io.File.separator + "mactime.pl";
            mactimeArgs = new String[]{macpath, "-b", pathToBodyFile, "-d", "-y"};
        }

        String macfile = moduleDir.getAbsolutePath() + java.io.File.separator + mactimeFileName;
        

        String output = "";
        ExecUtil execUtil = new ExecUtil();
        Writer writer = null;
        try {
            //JavaSystemCaller.Exec.execute("\"" + command + "\"");
            writer = new FileWriter(macfile);
            execUtil.execute(writer, cmdpath, mactimeArgs);
        } catch (InterruptedException ie) {
            logger.log(Level.WARNING, "Mactime process was interrupted by user", ie);
            return null;
        } catch (IOException ioe) {
            logger.log(Level.SEVERE, "Could not create mactime file, encountered error ", ioe);
            return null;
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException ex) {
                    logger.log(Level.SEVERE, "Could not clsoe writer after creating mactime file, encountered error ", ex);
                }
            }
        }

        return macfile;
    }

    @Override
    public boolean isEnabled() {
        return Case.isCaseOpen() && this.fxInited;
    }

    @Override
    public void performAction() {
        initTimeline();
    }

    private void initTimeline() {
        if (!Case.existsCurrentCase()) {
            return;
        }

        final Case currentCase = Case.getCurrentCase();
        skCase = currentCase.getSleuthkitCase();

        try {
            if (currentCase.getRootObjectsCount() == 0) {
                logger.log(Level.INFO, "Error creating timeline, there are no data sources. ");
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

                // if the timeline window is already open, bring to front and do nothing
                if (mainFrame != null && mainFrame.isVisible()) {
                    mainFrame.toFront();
                    return;
                }

                // listen for case changes (specifically images being added).
                if (Case.isCaseOpen() && !listeningToAddImage) {
                    Case.addPropertyChangeListener(this);
                    listeningToAddImage = true;
                }

                // create the modal progressDialog
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        progressDialog = new TimelineProgressDialog(WindowManager.getDefault().getMainWindow(), true);
                        progressDialog.setVisible(true);
                    }
                });

                // initialize mactimeFileName
                mactimeFileName = currentCase.getName() + "-MACTIME.txt";

                // see if barData has been added to the database since the last
                // time timeline ran
                long objId = skCase.getLastObjectId();
                if (objId != lastObjectId && lastObjectId != -1) {
                    clearMactimeData();
                }
                lastObjectId = objId;

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
