/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011 Basis Technology Corp.
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
import java.awt.Dimension;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.Stack;
import java.util.logging.Level;
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
import javax.swing.SwingUtilities;
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
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.corecomponents.DataContentTopComponent;
import org.sleuthkit.autopsy.corecomponents.DataResultTopComponent;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.datamodel.AbstractFsContentNode;
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
    @ActionReference(path = "Menu/Tools", position = 90)})
@NbBundle.Messages(value = "CTL_TimelineView=Generate Timeline")

public class Simile2 extends CallableSystemAction implements Presenter.Toolbar {
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
    private Button button_DrillUp;  //Navigation buttons
    private Button button_DrillDown;
    private Button button_Reset;
    private Button button_Go;
    private ComboBox<String> dropdown_SelectYears; //Dropdown box for selecting years. Useful when the charts' scale means some years are unclickable, despite having events.
    private final Stack<BarChart> stack_PrevCharts = new Stack<BarChart>();  //Stack for storing drill-up information.
    private BarChart chart_TopLevel; //the topmost chart, used for resetting to default view.
    private DataResultTopComponent dataResult;
    private DataContentTopComponent dataContent;
    
    //Swing components and JavafX components don't play super well together
    //Swing components need to be initialized first, in the swing specific thread
    //Next, the javafx components may be initialized.
    private void customizeSwing() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                dataContent = DataContentTopComponent.createUndocked("Content", Node.EMPTY);
                dataContent.setSize(1, 1);
                dataContent.setAlignmentX(Component.LEFT_ALIGNMENT);
                dataResult = DataResultTopComponent.createInstance("Results", "", Node.EMPTY, 0);
                dataResult.setAlignmentX(Component.LEFT_ALIGNMENT);
                dataResult.setPreferredSize(new Dimension(700, 300));
                logger.log(Level.INFO, "Successfully created viewers");
            }
        });
    }
    
    private void customize() {
        
        //Making the main frame *
        jf = new JFrame(Case.getCurrentCase().getName() + " - Autopsy Timeline");
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
        final JPanel comboJPanel = new JPanel();
        comboJPanel.setLayout(new BoxLayout(comboJPanel, BoxLayout.Y_AXIS));


        //JavaFX thread
        //JavaFX components MUST be run in the JavaFX thread, otherwise massive amounts of exceptions will be thrown and caught. Liable to freeze up and crash.
        //Components can be declared whenever, but initialization and manipulation must take place here.
        PlatformImpl.startup(new Runnable() {
            @Override
            public void run() {
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

                //mactime file placeholder, goes to pre-grenerated one on my desktop
                String bodyFilePath = makeBodyFile();
                java.io.File mactimePath = new java.io.File(makeMacTime(bodyFilePath));
                //java.io.File mactime = new java.io.File("C:" + java.io.File.separator + "Users" + java.io.File.separator + "nflower" + java.io.File.separator + "Downloads" + java.io.File.separator + "kanarazu-12-21-2012-11-15-46-mactime.txt");
                final List<YearEpoch> lsye = parseMacTime(mactimePath); //The sum total of the mactime parsing.  YearEpochs contain everything you need to make a timeline.

                //Making a dropdown box to select years.
                List<String> lsi = new ArrayList<String>();  //List is in the format of {Year : Number of Events}, used for selecting from the dropdown.
                for (YearEpoch ye : lsye) {
                    lsi.add(ye.year + " : " + ye.total);
                }
                ObservableList<String> listSelect = FXCollections.observableArrayList(lsi);
                dropdown_SelectYears = new ComboBox(listSelect);

                //Buttons for navigating up and down the timeline
                button_DrillDown = new Button("Drill down");
                button_DrillDown.setOnAction(new EventHandler<ActionEvent>() {
                    @Override
                    public void handle(ActionEvent e) {
                        //Placeholder, does nothing. Need to store a chart_LastSelected or something
                    }
                });
                button_DrillUp = new Button("Drill up");
                button_DrillUp.setOnAction(new EventHandler<ActionEvent>() {
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
                button_Reset = new Button("Reset");
                button_Reset.setOnAction(new EventHandler<ActionEvent>() {
                    @Override
                    public void handle(ActionEvent e) {
                        stack_PrevCharts.clear();
                        chart_Events = chart_TopLevel;
                        scroll_Events.setContent(chart_Events);
                    }
                });
                button_Go = new Button("►");
                button_Go.setOnAction(new EventHandler<ActionEvent>() {
                    @Override
                    public void handle(ActionEvent e) {
                        if (dropdown_SelectYears.getValue() != null) {
                            chart_Events = createMonthsWithDrill(findYear(lsye, Integer.valueOf(dropdown_SelectYears.getValue().split(" ")[0])));
                            scroll_Events.setContent(chart_Events);
                        }
                    }
                });

                //Adding things to the V and H boxes. 
                //hBox_Charts stores the pseudo menu bar at the top of the timeline. |Drill Up|Drill Down|Reset|View Year: [Select Year]|►|
                hBox_Charts.getChildren().addAll(button_DrillUp, button_DrillDown, button_Reset, new Label("View Year:"), dropdown_SelectYears, button_Go);
                vBox_FX.getChildren().addAll(hBox_Charts, scroll_Events); //FxBox_V holds things in a visual stack. 
                group_Charts.getChildren().add(vBox_FX); //Adding the FxBox to the group. Groups make things easier to manipulate without having to update a hundred things every change.
                panel_Charts.setScene(scene_Charts);


                panel_Charts.setAlignmentX(Component.LEFT_ALIGNMENT);

                chartJPanel.add(panel_Charts);
                viewerJPanel.add(dataResult);
                viewerJPanel.add(dataContent);
                chartJPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
                viewerJPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
                comboJPanel.add(chartJPanel);
                comboJPanel.add(viewerJPanel);

                chart_TopLevel = createYearChartWithDrill(lsye);
                chart_Events = chart_TopLevel;
                scroll_Events.setContent(chart_Events);
                jf.add(comboJPanel);
                jf.setVisible(true);
            }
        });
    }

    /**
     * Creates a BarChart with datapoints for all the years from the parsed
     * mactime file.
     *
     * @param allYears The list of years that have data from the mactime file
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
        //Charts are made up of individual pieces of Chart.Data. In this case, a piece of data is a single bar on the graph.
        //Data is packaged into a series, which can be assigned custom colors or styling
        //After the series are created, 1 or more series are packaged into a single chart.
        ObservableList<BarChart.Series<String, Number>> bcData = FXCollections.observableArrayList();
        BarChart.Series<String, Number> se = new BarChart.Series<String, Number>();
        for (final YearEpoch ye : allYears) {
            se.getData().add(new BarChart.Data<String, Number>(String.valueOf(ye.year), ye.total));
        }
        bcData.add(se);

        //Note: 
        // BarChart.Data wraps the Java Nodes class. BUT, until a BarChart.Data gets added to an actual series, it's node is null, and you can perform no operations on it.
        // When the Data is added to a series(or a chart? I am unclear on where), a node is automaticaly generated for it, after which you can perform any of the operations it offers. 
        // In addtion, you are free to set the node to whatever you want. It wraps the most generic Node class.
        // But it is for this reason that the chart generating functions have two forloops. I do not believe they can be condensed into a single loop due to the nodes being null until 
        // an undetermined point in time. 
        BarChart<String, Number> bc = new BarChart<String, Number>(xAxis, yAxis, bcData);
        for (final BarChart.Data data : bc.getData().get(0).getData()) { //.get(0) refers to the BarChart.Series class to work on. There is only one series in this graph, so get(0) is safe.
            data.getNode().setScaleX(.5);
            data.getNode().addEventHandler(MouseEvent.MOUSE_CLICKED,
                    new EventHandler<MouseEvent>() {
                        @Override
                        public void handle(MouseEvent e) {
                            if (e.getButton().equals(MouseButton.PRIMARY)) {
                                if (e.getClickCount() == 2) { //Checking for a doubleclick
                                    PlatformImpl.startup(new Runnable() {
                                        @Override
                                        public void run() {
                                            BarChart b = createMonthsWithDrill((YearEpoch) findYear(allYears, Integer.valueOf((String) data.getXValue())));
                                            chart_Events = b;
                                            scroll_Events.setContent(chart_Events);
                                        }
                                    });
                                    //If a single click, hover a label over the cursor with information about the selection
                                } else if (e.getClickCount() == 1) {
                                    l.setText(findYear(allYears, Integer.valueOf((String) data.getXValue())).total + " events");
                                    l.setTranslateX(e.getX());
                                    l.setTranslateY(e.getY());
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
        for (final MonthEpoch me : ye.months) {
            se.getData().add(new BarChart.Data<String, Number>(me.getMonthName(), me.total)); //Adding new data at {X-pos, Y-Pos}
        }
        bcData.add(se);
        final BarChart<String, Number> bc = new BarChart<String, Number>(xAxis, yAxis, bcData);

        for (int i = 0; i < 12; i++) {
            for (final BarChart.Data data : bc.getData().get(0).getData()) {
                //Note: 
                // All the charts of this package have a problem where when the chart gets below a certain pixel ratio, the data stops drawing. The axes and the labels remain, 
                // But the actual chart data is invisible, unclickable, and unrendered. To partially compensate for that, data.getNode() can be manually scaled up to increase visibility.
                // Sometimes I've had it jacked up to as much as x2400 just to see a sliver of information.
                // But that doesn't work all the time. Adding it to a scrollpane and letting the user scroll up and down to view the chart is the other workaround. Both of these fixes suck.

                data.getNode().setScaleX(.5); 
                data.getNode().addEventHandler(MouseEvent.MOUSE_PRESSED,
                        new EventHandler<MouseEvent>() {
                            @Override
                            public void handle(MouseEvent e) {
                                if (e.getButton().equals(MouseButton.PRIMARY)) {
                                    if (e.getClickCount() == 2) {
                                        PlatformImpl.startup(new Runnable() {
                                            @Override
                                            public void run() {
                                                chart_Events = createEventsByMonth(findMonth(ye.months, month_StringtoInt((String) data.getXValue())));
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
    private BarChart createEventsByMonth(final MonthEpoch me) {
        final CategoryAxis xAxis = new CategoryAxis();
        final NumberAxis yAxis = new NumberAxis();
        xAxis.setLabel("Day of Month");
        yAxis.setLabel("Number of Events");
        ObservableList<BarChart.Data> bcData = makeObservableListByMonthAllDays(me);
        BarChart.Series<String, Number> series = new BarChart.Series(bcData);
        series.setName(me.getMonthName() + " " + me.year);

        ObservableList<BarChart.Series<String, Number>> ol = FXCollections.observableArrayList(series);

        final BarChart<String, Number> bc = new BarChart<String, Number>(xAxis, yAxis, ol);
        for (final BarChart.Data data : bc.getData().get(0).getData()) {
            //data.getNode().setScaleX(2);
            data.getNode().addEventHandler(MouseEvent.MOUSE_PRESSED,
                    new EventHandler<MouseEvent>() {
                        @Override
                        public void handle(MouseEvent e) {
                            final FsContentRootNode d = new FsContentRootNode("Test Root", (((MonthEpoch) data.getExtraValue()).getDays().get(Integer.valueOf(((String) data.getXValue()).split("-")[1]) - 1)).getEvents());
                            SwingUtilities.invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    dataResult.setNode(d);
                                }
                            });
                        }
                    });
        }
        bc.autosize();
        bc.setPrefWidth(Width_Frame);
        return bc;
    }

    private ObservableList<BarChart.Data> makeObservableListByMonthAllDays(final MonthEpoch me) {
        ObservableList<BarChart.Data> bcData = FXCollections.observableArrayList();
        for (DayEpoch day : me.getDays()) {
            BarChart.Data d = new BarChart.Data(me.month + 1 + "-" + String.valueOf(day.dayNum), day.events.size());
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
     * @param mon The month to convert. Must be minimum 4 characters long "February" and "Febr" are acceptable.
     * @return The integer value of the month. February = 1, July = 6
     */
    private int month_StringtoInt(String mon) {
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
     * @param lst The list of months to search through. It is assumed that the desired match is in this list.
     * @param match The month, in integer format, to retrieve. 
     * @return The month epoch as specified by match.
     */
    private MonthEpoch findMonth(List<MonthEpoch> lst, int match) {
        for (MonthEpoch e : lst) {
            if (e.month == match) {
                return e;
            }
        }
        return null;
    }

       /**
     * Used for finding the proper year in a list of available years
     * @param lst The list of years to search through. It is assumed that the desired match is in this list.
     * @param match The year to retrieve. 
     * @return The year epoch as specified by match.
     */
    private YearEpoch findYear(List<YearEpoch> lst, int match) {
        for (YearEpoch e : lst) {
            if (e.year == match) {
                return e;
            }
        }
        return null;
    }

    /*
     * The backbone of the timeline functionality, years are split into months, months into days, and days contain the events of that given day.
     * All of those are Epochs.
     */
    abstract class Epoch {
        Calendar calendar;
        int year;
        boolean isDay = false;
        boolean isMonth = false;
        boolean isYear = false;
        int total = 0; //total numder of events for this epoch

        Epoch(int year) {
            this.year = year;
            this.calendar = Calendar.getInstance();
        }
    }

    private class YearEpoch extends Epoch {

        List<MonthEpoch> months;
        int numDays = 0; //Days in this year. Will be between 366 and 365

        YearEpoch(int year, List<MonthEpoch> months) {
            super(year);
            this.calendar.set(year, 0, 1); //default to January 1st
            isYear = true;
            this.months = months;
            for (MonthEpoch me : months) {
                total += me.total;
                numDays += me.numDays;
            }
        }
    }

    private class MonthEpoch extends Epoch {

        int month; //Zero-indexed: June = 5, August = 7, etc
        String month_name; //Full name: January, October, July, etc
        List<DayEpoch> days; //List of DayEpochs in this month, max 31
        int numDays = 0; //Number of days in this month, max 31

        MonthEpoch(int month, int year, List<DayEpoch> days) {
            super(year);
            isMonth = true;
            this.month = month;
            calendar.set(year, month, 1);
            java.util.Date d = new java.util.Date(calendar.getTimeInMillis());
            this.month_name = new SimpleDateFormat("MMMM").format(d);
            this.numDays = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);
            this.year = year;
            this.days = days;
            for (DayEpoch de : days) {
                total += de.total;
            }
        }

        /**
         * Returns the month's name in String format, e.g., September, July,
         */
        String getMonthName() {
            return this.month_name;
        }

        /**
         * @return the list of days in this month
         */
        List<DayEpoch> getDays() {
            return this.days;
        }
    }

    private class DayEpoch extends Epoch {

        private List<AbstractFile> events;
        int dayNum = 0; //Day of the month this Epoch represents, 1 indexed: 28=28.

        DayEpoch(int year, int month, int day, List<AbstractFile> events) {
            super(year);
            isDay = true;
            this.dayNum = day;
            this.events = events;
            total = events.size();
        }

        //Constructor for null-days to fill out a Month.
        DayEpoch(int year, int month, int day) {
            super(year);
            this.dayNum = day;
            this.events = new ArrayList<AbstractFile>();
            this.total = 0;
        }

        List<AbstractFile> getEvents() {
            return this.events;
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
            Node n;
            if (file.isDir()) {
                n = new DirectoryNode((Directory) file);
            } else {
                n = new FileNode((File) file);
            }
            return n;
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

    /**
     * Fills an array list with empty day objects according to the number of
     * days in a month
     *
     * @param daysToFill
     * @param daysInMonth Must be Zero Indexed
     */
    private ArrayList<DayEpoch> fillOutDays(DayEpoch[] daysToFill, int daysInMonth, int year, int month) {
        for (int i = 0; i < daysInMonth; i++) {
            if (daysToFill[i] == null) {
                daysToFill[i] = new DayEpoch(year, month, i + 1);
            }
        }
        return new ArrayList<DayEpoch>(Arrays.asList(daysToFill));
    }

    private ArrayList<MonthEpoch> fillOutMonths(MonthEpoch[] monthsToFill, int year) {
        for (int i = 0; i < 12; i++) {
            if (monthsToFill[i] == null) {
                Calendar cal2 = Calendar.getInstance();
                cal2.set(year, i, 1);
                int daysinMonth = cal2.getActualMaximum(Calendar.DAY_OF_MONTH) - 1;
                monthsToFill[i] = new MonthEpoch(i, year, fillOutDays(new DayEpoch[daysinMonth], daysinMonth, year, i));
            }
        }
        return new ArrayList<MonthEpoch>(Arrays.asList(monthsToFill));
    }

    private List<YearEpoch> parseMacTime(java.io.File f) {
        try {
            boolean firstRun = true; //used to help method not screw up on the first run
            Scanner scan = new Scanner(new FileInputStream(f));
            scan.nextLine();
            SleuthkitCase skCase = Case.getCurrentCase().getSleuthkitCase();
            Calendar cal = Calendar.getInstance();
            int daysInThisMonth = 31;   //Number of days in the currentMonth
            scan.useDelimiter(",");
            String prevDate = "";

            int prevYear = -1;
            int prevMonth = -1;
            int prevDay = -1;
            long prevObjId = -1;
            String prevType = "-1";


            List<AbstractFile> fs = new ArrayList<AbstractFile>(); //Single day's events
            DayEpoch[] dayz = new DayEpoch[31]; //Default initialization to 31 slots
            MonthEpoch[] monthz = new MonthEpoch[12];
            List<YearEpoch> years = new ArrayList<YearEpoch>();

            while (scan.hasNextLine()) {
                AbstractFsContentNode n;
                String[] s = scan.nextLine().split(","); //1999-02-08T11:08:08Z, 78706, m..b, rrwxrwxrwx, 0, 0, 8355, /img...
                String[] datetime = s[0].split("T"); //{1999-02-08, 11:08:08Z}
                String[] date = datetime[0].split("-"); // {1999, 02, 08}
                int year = Integer.valueOf(date[0]);
                int month = Integer.valueOf(date[1]) - 1; //Months are zero indexed: 1 = February, 6 = July, 11 = December
                int day = Integer.valueOf(date[2]); //Days are 1 indexed
                String type = s[2];
                long ObjId = Long.valueOf(s[4]);

                //First values 
                if (firstRun) {
                    prevDate = datetime[0];
                    prevYear = year;
                    prevMonth = month;
                    prevDay = day;
                    prevObjId = ObjId;
                    prevType = type;
                    cal.set(prevYear, prevMonth, 1);
                    daysInThisMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
                    dayz = new DayEpoch[daysInThisMonth];
                    firstRun = false;
                }

                /**
                 * End Testing Stuff
                 */
                //Intermediate values
                if ((!prevDate.equals(datetime[0]))) {  //Did the Day change?
                    cal.set(prevYear, prevMonth, 1);
                    DayEpoch d = new DayEpoch(prevYear, prevMonth, prevDay, fs);
                    dayz[prevDay - 1] = d;
                    fs = new ArrayList<AbstractFile>();
                    prevDate = datetime[0];
                    prevDay = day;

                    if (month != prevMonth) { //Did the Month change?
                        ArrayList<DayEpoch> da = fillOutDays(dayz, daysInThisMonth, prevYear, prevMonth);
                        MonthEpoch m = new MonthEpoch(prevMonth, prevYear, da);
                        monthz[prevMonth] = m;
                        prevMonth = month;
                        cal.set(prevYear, prevMonth, 1);
                        daysInThisMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
                        dayz = new DayEpoch[daysInThisMonth];

                        if (Integer.valueOf(year) != prevYear) {   //Did the Year change?
                            YearEpoch y = new YearEpoch(prevYear, fillOutMonths(monthz, prevYear));
                            monthz = new MonthEpoch[12];
                            years.add(y);
                            prevYear = Integer.valueOf(year);
                        }
                    }
                    prevObjId = ObjId;
                    prevType = type;
                } else {
                    AbstractFile file = skCase.getAbstractFileById(ObjId);
                    fs.add(file);
                }
            }
            //Final values
            cal.set(prevYear, prevMonth, 1);
            DayEpoch d = new DayEpoch(prevYear, prevMonth, prevDay, fs);
            dayz[prevDay - 1] = d;
            ArrayList<DayEpoch> da = fillOutDays(dayz, daysInThisMonth, prevYear, prevMonth);
            MonthEpoch m = new MonthEpoch(prevMonth, prevYear, da);
            monthz[prevMonth] = m;
            YearEpoch y = new YearEpoch(prevYear, fillOutMonths(monthz, prevYear));
            years.add(y);
            return years;
        } catch (FileNotFoundException fnfe) {
            logger.log(Level.SEVERE, "Error 404: can't find Mactime file", fnfe);
            return null;
        } catch (TskCoreException tce) {
            logger.log(Level.SEVERE, "Error 500: can't parse mactime file", tce);
            return null;
        }
    }

    private String makeBodyFile() {
        // Setup timestamp
        DateFormat dateFormat = new SimpleDateFormat("MM-dd-yyyy-HH-mm-ss");
        Date date = new Date();
        String datenotime = dateFormat.format(date);

        Case currentCase = Case.getCurrentCase();
        SleuthkitCase skCase = currentCase.getSleuthkitCase();
        // Get report path
        String bodyFilePath = currentCase.getCaseDirectory() + java.io.File.separator + "temp"
                + java.io.File.separator + currentCase.getName() + "-" + datenotime + ".txt";

        // Run query to get all files
        ResultSet rs = null;
        try {
            // exclude non-fs files/dirs and . and .. files
            rs = skCase.runQuery("SELECT * FROM tsk_files "
                    + "WHERE type = '" + TskData.TSK_DB_FILES_TYPE_ENUM.FS.getFileType() + "' "
                    + "AND name != '.' "
                    + "AND name != '..'");
            List<FsContent> fs = skCase.resultSetToFsContents(rs);
            // Loop files and write info to report
            for (FsContent file : fs) {

                BufferedWriter out = null;
                try {
                    // MD5|name|inode|mode_as_string|ObjId|GID|size|atime|mtime|ctime|crtime
                    out = new BufferedWriter(new FileWriter(bodyFilePath, true));

                    if (file.getMd5Hash() != null) {
                        out.write(file.getMd5Hash());
                    }
                    out.write("|");
                    if (file.getUniquePath() != null) {
                        out.write(file.getUniquePath());
                    }
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
                } catch (IOException ex) {
                    logger.log(Level.WARNING, "Could not write the temp body file report.", ex);
                } finally {
                    try {
                        out.flush();
                        out.close();
                    } catch (IOException ex) {
                        logger.log(Level.WARNING, "Could not flush and close the BufferedWriter.", ex);
                    }
                }
            }
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to get all file information.", ex);
        } catch (TskCoreException ex) {
            logger.log(Level.WARNING, "Failed to get the unique path.", ex);
        } finally {
            try {// Close the query
                if (rs != null) {
                    skCase.closeRunQuery(rs);
                }
            } catch (SQLException ex) {
                logger.log(Level.WARNING, "Failed to close the query.", ex);
            }
        }
        return bodyFilePath;
    }

    private String makeMacTime(String pathToBodyFile) {
        String macpath = "";
        final String machome = macRoot.getAbsolutePath();
        if (PlatformUtil.isWindowsOS()) {
            macpath = machome + java.io.File.separator + "mactime.exe";
        } else {
            macpath = "perl " + machome + java.io.File.separator + "mactime.pl";
        }
        String macfile = Case.getCurrentCase().getCaseDirectory() + java.io.File.separator + "temp" + java.io.File.separator + Case.getCurrentCase().getName() + "-MACTIME.txt";
        if (macRoot != null) {
            String command = macpath + " -b " + pathToBodyFile + " -d " + " -y " + ">" + macfile;
            try {
                JavaSystemCaller.Exec.execute("\"" + command + "\"");
                return macfile;
            } catch (InterruptedException ie) {
                logger.log(Level.WARNING, "Mactime process was interrupted by user", ie);
            } catch (IOException ioe) {
                logger.log(Level.SEVERE, "Could not create mactime file, encountered error ", ioe);
            }
        } else {
            System.out.println("Macroot was null");
        }
        return null;
    }

    


    @Override
    public void performAction() {
        if(IngestManager.getDefault().isIngestRunning()){
            int i = JOptionPane.showConfirmDialog(new JFrame(), "You are trying to generate a timeline before ingest has been completed. The timeline may be incomplete. Do you want to continue?");
            if (i != JOptionPane.YES_OPTION){
                return;
            }
        }else{
                logger.log(Level.INFO, "Beginning generation of timeline");
                customizeSwing();
                customize();
        }
    }

    @Override
    public String getName() {
        return "Make Timeline";
    }

    @Override
    public HelpCtx getHelpCtx() {
        return HelpCtx.DEFAULT_HELP;
    }

    @Override
    public boolean asynchronous() {
        return false;
    }
    //<editor-fold defaultstate="collapsed" desc="Old Functions">
    /**
     * Old Functions *
     */
    //    private BarChart createEventsByYear(final YearEpoch ye) {
    //        final CategoryAxis xAxis = new CategoryAxis();
    //        final NumberAxis yAxis = new NumberAxis();
    //        final int maxEvents = ye.max;
    //        final int maxNumDays = ye.numDays;
    //        xAxis.setLabel("Day of Year");
    //        yAxis.setLabel("Number of Events");
    //        ObservableList<BarChart.Series<String, Number>> seriesList = FXCollections.observableArrayList();
    //        for (final MonthEpoch me : ye.months) {
    //            BarChart.Series<String, Number> series = new BarChart.Series(makeObservableListByMonthAllDays(me));
    //            series.setName(me.getMonthName() + " " + me.year);
    //            seriesList.add(series);
    //        }
    //        final BarChart<String, Number> bc = new BarChart<String, Number>(xAxis, yAxis, seriesList);
    //        for (int i = 0; i < bc.getData().size(); i++) {
    //            for (final BarChart.Data data : bc.getData().get(i).getData()) {
    //                data.getNode().setScaleX(240);
    //                data.getNode().addEventHandler(MouseEvent.MOUSE_PRESSED,
    //                        new EventHandler<MouseEvent>() {
    //                            @Override
    //                            public void handle(MouseEvent e) {
    //                                final FsContentRootNode d = new FsContentRootNode("Test Year Nodes", (((MonthEpoch) data.getExtraValue()).getDays().get(Integer.valueOf(((String) data.getXValue()).split("-")[1]))).getEvents());
    //                                SwingUtilities.invokeLater(new Runnable() {
    //                                    @Override
    //                                    public void run() {
    //                                        dataResult.setNode(d);
    //                                    }
    //                                });
    //                            }
    //                        });
    //            }
    //        }
    //        bc.autosize();
    //        bc.setPrefWidth(63 * maxNumDays);
    //        //bc.setScaleX(1.25);
    //        //bc.setMinSize(30 * maxNumDays, 4 * maxEvents); // 40 * numDays in year, 4 * maxEvents
    //        //bc.setMaxSize(30 * maxNumDays, 4 * maxEvents); //Width, Height
    //        return bc;
    //    }
    /*
     * the small 12-month chart
     */
    //    private BarChart createMonthBarChartFromYear(final YearEpoch ye) {
    //        final CategoryAxis xAxis = new CategoryAxis();
    //        final NumberAxis yAxis = new NumberAxis();
    //        final int numMonths = 12;
    //        final int maxEvents = ye.max;
    //        xAxis.setLabel("Month (" + ye.year + ")");
    //        yAxis.setLabel("Number of Events");
    //        ObservableList<BarChart.Series<String, Number>> bcData = FXCollections.observableArrayList();
    //
    //        BarChart.Series<String, Number> se = new BarChart.Series<String, Number>();
    //        for (final MonthEpoch me : ye.months) {
    //            se.getData().add(new BarChart.Data<String, Number>(me.getMonthName(), me.total));
    //        }
    //        bcData.add(se);
    //
    //
    //        final BarChart<String, Number> bc = new BarChart<String, Number>(xAxis, yAxis, bcData);//bcData);
    //        for (int i = 0; i < numMonths; i++) {
    //            for (final BarChart.Data data : bc.getData().get(0).getData()) {
    //                data.getNode().setScaleX(26);
    //                data.getNode().addEventHandler(MouseEvent.MOUSE_PRESSED,
    //                        new EventHandler<MouseEvent>() {
    //                            @Override
    //                            public void handle(MouseEvent e) {
    //
    //                                PlatformImpl.startup(new Runnable() {
    //                                    @Override
    //                                    public void run() {
    //                                        chart_Events = createEventsByMonth(findMonth(ye.months, month_StringtoInt((String) data.getXValue())));
    //                                        scroll_Events.setContent(chart_Events);
    //                                    }
    //                                });
    //
    //                            }
    //                        });
    //            }
    //        }
    //
    //        bc.setPrefHeight(Height_PieBoxes);
    //        bc.setPrefWidth(Width_PieBoxes);
    //        bc.setLegendVisible(false);
    //        return bc;
    //    }
    //    boolean checkCache(List<YearEpoch> epochs, int match) {
    //        for (Epoch e : epochs) {
    //            if (e.year == match) {
    //                return true;
    //            }
    //        }
    //        return false;
    //    }
    //
    //    YearEpoch getFromCache(List<YearEpoch> epochs, int match) {
    //        for (YearEpoch e : epochs) {
    //            if (e.year == match) {
    //                return e;
    //            }
    //        }
    //        return null;
    //    }
    //    private PieChart initYearChart() {
    //        ObservableList<PieChart.Data> pieChartData =
    //                FXCollections.observableArrayList();
    //
    //        double totalEvents = 0.0;
    //        String sep = java.io.File.separator;
    //        java.io.File mactime = new java.io.File("C:" + sep + "Users" + sep + "nflower" + sep + "Downloads" + sep + "kanarazu-12-21-2012-11-15-46-mactime.txt");
    //        List<YearEpoch> ls = parseMacTime(mactime);
    //
    //        for (int i = 0; i < ls.size(); i++) {
    //            cachedYears.add(ls.get(i));
    //            totalEvents += ls.get(i).total;
    //        }
    //
    //        for (YearEpoch e : ls) {
    //            PieChart.Data d = new PieChart.Data(String.valueOf(e.year), (e.total / totalEvents) * 100);
    //            pieChartData.add(d);
    //        }
    //        final PieChart chart = new PieChart(pieChartData);
    //        chart.setTitle("Years with Activity");
    //        for (int i = 0; i < chart.getData().size(); i++) {
    //            final PieChart.Data data = chart.getData().get(i);
    //            final YearEpoch y = findYear(ls, Integer.valueOf(data.getName()));
    //            data.getNode().setUserData(y);
    //            data.getNode().addEventHandler(MouseEvent.MOUSE_PRESSED,
    //                    new EventHandler<MouseEvent>() {
    //                        @Override
    //                        public void handle(MouseEvent e) {
    //                            chart_Months = createMonthBarChartFromYear(((YearEpoch) data.getNode().getUserData()));
    //                            scroll_Months.setContent(chart_Months);
    //
    //                            chart_Events = createEventsByYear(y);
    //                            scroll_Events.setContent(chart_Events);
    //                            chart_Events.getBoundsInParent();
    //                        }
    //                    });
    //
    //        }
    //        chart.setLegendVisible(false);
    //        chart.setPrefHeight(Height_PieBoxes / 1.3);
    //        chart.setPrefWidth(Width_PieBoxes / 1.1);
    //        return chart;
    //    }
    //</editor-fold>
}
