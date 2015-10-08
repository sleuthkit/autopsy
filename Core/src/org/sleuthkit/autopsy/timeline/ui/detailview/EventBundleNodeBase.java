/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.timeline.ui.detailview;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import static javafx.scene.layout.Region.USE_COMPUTED_SIZE;
import static javafx.scene.layout.Region.USE_PREF_SIZE;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import org.joda.time.DateTime;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.datamodel.EventBundle;
import org.sleuthkit.autopsy.timeline.datamodel.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.datamodel.TimeLineEvent;
import org.sleuthkit.autopsy.timeline.datamodel.eventtype.EventType;
import static org.sleuthkit.autopsy.timeline.ui.detailview.EventBundleNodeBase.show;
import org.sleuthkit.autopsy.timeline.zooming.DescriptionLoD;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 */
public abstract class EventBundleNodeBase<BundleType extends EventBundle<ParentType>, ParentType extends EventBundle<BundleType>, ParentNodeType extends EventBundleNodeBase<ParentType, BundleType, ?>> extends StackPane {

    private static final Logger LOGGER = Logger.getLogger(EventBundleNodeBase.class.getName());
    private static final Image HASH_PIN = new Image("/org/sleuthkit/autopsy/images/hashset_hits.png"); //NOI18N
    private static final Image TAG = new Image("/org/sleuthkit/autopsy/images/green-tag-icon-16.png"); // NON-NLS //NOI18N

    static final CornerRadii CORNER_RADII_3 = new CornerRadii(3);
    static final CornerRadii CORNER_RADII_1 = new CornerRadii(1);

    private static final Border SELECTION_BORDER = new Border(new BorderStroke(Color.BLACK, BorderStrokeStyle.SOLID, CORNER_RADII_3, new BorderWidths(2)));
    private static final Map<EventType, DropShadow> dropShadowMap = new ConcurrentHashMap<>();

    static void configureLoDButton(Button b) {
        b.setMinSize(16, 16);
        b.setMaxSize(16, 16);
        b.setPrefSize(16, 16);
        show(b, false);
    }

    static void show(Node b, boolean show) {
        b.setVisible(show);
        b.setManaged(show);
    }

    protected final EventDetailChart chart;
    final SimpleObjectProperty<DescriptionLoD> descLOD = new SimpleObjectProperty<>();
    protected final BundleType eventBundle;

    protected final ParentNodeType parentNode;

    final SleuthkitCase sleuthkitCase;
    final FilteredEventsModel eventsModel;

    final Background highlightedBackground;
    final Background defaultBackground;
    final Color evtColor;

    final List<ParentNodeType> subNodes = new ArrayList<>();
    final Pane subNodePane = new Pane();
    final Label descrLabel = new Label();
    final Label countLabel = new Label();

    final ImageView hashIV = new ImageView(HASH_PIN);
    final ImageView tagIV = new ImageView(TAG);
    final HBox infoHBox = new HBox(5, descrLabel, countLabel, hashIV, tagIV);

    private Tooltip tooltip;

    public EventBundleNodeBase(EventDetailChart chart, BundleType eventBundle, ParentNodeType parentNode) {
        this.eventBundle = eventBundle;
        this.parentNode = parentNode;
        this.chart = chart;

        this.descLOD.set(eventBundle.getDescriptionLoD());
        sleuthkitCase = chart.getController().getAutopsyCase().getSleuthkitCase();
        eventsModel = chart.getController().getEventsModel();
        evtColor = getEventType().getColor();
        defaultBackground = new Background(new BackgroundFill(evtColor.deriveColor(0, 1, 1, .1), CORNER_RADII_3, Insets.EMPTY));
        highlightedBackground = new Background(new BackgroundFill(evtColor.deriveColor(0, 1.1, 1.1, .3), CORNER_RADII_3, Insets.EMPTY));

        if (eventBundle.getEventIDsWithHashHits().isEmpty()) {
            show(hashIV, false);
        }
        if (eventBundle.getEventIDsWithTags().isEmpty()) {
            show(tagIV, false);
        }

        setBackground(defaultBackground);
        setAlignment(Pos.TOP_LEFT);
//        setMinHeight(24);
        setPrefHeight(USE_COMPUTED_SIZE);
        setMaxHeight(USE_PREF_SIZE);
        setMaxWidth(USE_PREF_SIZE);
        setLayoutX(chart.getXAxis().getDisplayPosition(new DateTime(eventBundle.getStartMillis())) - getLayoutXCompensation());

        //initialize info hbox
        infoHBox.setMinWidth(USE_PREF_SIZE);
        infoHBox.setMaxWidth(USE_PREF_SIZE);
        infoHBox.setPadding(new Insets(2, 5, 2, 5));
        infoHBox.setAlignment(Pos.TOP_LEFT);
        infoHBox.setPickOnBounds(true);

        //set up subnode pane sizing contraints
        subNodePane.setPrefHeight(USE_COMPUTED_SIZE);
//        subNodePane.setMinHeight(24);
        subNodePane.setMaxHeight(USE_PREF_SIZE);
        subNodePane.setPrefWidth(USE_COMPUTED_SIZE);
        subNodePane.setMinWidth(USE_PREF_SIZE);
        subNodePane.setMaxWidth(USE_PREF_SIZE);

        //set up mouse hover effect and tooltip
        setOnMouseEntered((MouseEvent e) -> {
            /*
             * defer tooltip creation till needed, this had a surprisingly large
             * impact on speed of loading the chart
             */
            installTooltip();
            showHoverControls(true);
            toFront();
        });
        setOnMouseExited((MouseEvent event) -> {
            showHoverControls(false);
            if (parentNode != null) {
                parentNode.showHoverControls(true);
            }
        });
    }

    final DescriptionLoD getDescriptionLoD() {
        return descLOD.get();
    }

    public final BundleType getEventBundle() {
        return eventBundle;
    }

    final double getLayoutXCompensation() {
        return (parentNode != null ? parentNode.getLayoutXCompensation() : 0)
                + getBoundsInParent().getMinX();
    }

    @NbBundle.Messages({"# {0} - counts",
        "# {1} - event type",
        "# {2} - description",
        "# {3} - start date/time",
        "# {4} - end date/time",
        "EventBundleNodeBase.tooltip.text={0} {1} events\n{2}\nbetween\t{3}\nand   \t{4}"})
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private void installTooltip() {
        if (tooltip == null) {
            final Task<String> tooltTipTask = new Task<String>() {

                @Override
                protected String call() throws Exception {
                    HashMap<String, Long> hashSetCounts = new HashMap<>();
                    if (eventBundle.getEventIDsWithHashHits().isEmpty() == false) {
                        try {
                            //TODO:push this to DB
                            for (TimeLineEvent tle : eventsModel.getEventsById(eventBundle.getEventIDsWithHashHits())) {
                                Set<String> hashSetNames = sleuthkitCase.getAbstractFileById(tle.getFileID()).getHashSetNames();
                                for (String hashSetName : hashSetNames) {
                                    hashSetCounts.merge(hashSetName, 1L, Long::sum);
                                }
                            }
                        } catch (TskCoreException ex) {
                            LOGGER.log(Level.SEVERE, "Error getting hashset hit info for event.", ex);
                        }
                    }
                    String hashSetCountsString = hashSetCounts.entrySet().stream()
                            .map((Map.Entry<String, Long> t) -> t.getKey() + " : " + t.getValue())
                            .collect(Collectors.joining("\n"));

                    Map<String, Long> tagCounts = new HashMap<>();
                    if (eventBundle.getEventIDsWithTags().isEmpty() == false) {
                        tagCounts.putAll(eventsModel.getTagCountsByTagName(eventBundle.getEventIDsWithTags()));
                    }
                    String tagCountsString = tagCounts.entrySet().stream()
                            .map((Map.Entry<String, Long> t) -> t.getKey() + " : " + t.getValue())
                            .collect(Collectors.joining("\n"));

                    return Bundle.EventBundleNodeBase_tooltip_text(getEventIDs().size(), getEventType(), getDescription(),
                            TimeLineController.getZonedFormatter().print(getStartMillis()),
                            TimeLineController.getZonedFormatter().print(getEndMillis() + 1000))
                            + (hashSetCountsString.isEmpty() ? "" : "\n\nHash Set Hits\n" + hashSetCountsString)
                            + (tagCountsString.isEmpty() ? "" : "\n\nTags\n" + tagCountsString);
                }

                @Override
                protected void succeeded() {
                    super.succeeded();
                    try {
                        tooltip = new Tooltip(get());
                        tooltip.setAutoHide(true);
                        Tooltip.install(EventBundleNodeBase.this, tooltip);
                    } catch (InterruptedException | ExecutionException ex) {
                        LOGGER.log(Level.SEVERE, "Tooltip generation failed.", ex);
                        Tooltip.uninstall(EventBundleNodeBase.this, tooltip);
                        tooltip = null;
                    }
                }
            };

            chart.getController().monitorTask(tooltTipTask);
        }
    }

    /**
     * apply the 'effect' to visually indicate selection
     *
     * @param applied true to apply the selection 'effect', false to remove it
     */
    public void applySelectionEffect(boolean applied) {
        setBorder(applied ? SELECTION_BORDER : null);
    }

    /**
     * apply the 'effect' to visually indicate highlighted nodes
     *
     * @param applied true to apply the highlight 'effect', false to remove it
     */
    abstract void applyHighlightEffect(boolean applied);

    @SuppressWarnings("unchecked")
    public List<ParentNodeType> getSubNodes() {
        return subNodes;
    }

    abstract void setDescriptionVisibility(DescriptionVisibility get);

    void showHoverControls(final boolean showControls) {
        DropShadow dropShadow = dropShadowMap.computeIfAbsent(getEventType(),
                eventType -> new DropShadow(10, eventType.getColor()));
        setEffect(showControls ? dropShadow : null);
        if (parentNode != null) {
            parentNode.showHoverControls(false);
        }
    }

    final EventType getEventType() {
        return getEventBundle().getEventType();
    }

    final String getDescription() {
        return getEventBundle().getDescription();
    }

    final long getStartMillis() {
        return getEventBundle().getStartMillis();
    }

    final long getEndMillis() {
        return getEventBundle().getEndMillis();
    }

    final Set<Long> getEventIDs() {
        return getEventBundle().getEventIDs();
    }

    @Override
    protected void layoutChildren() {
        double chartX = chart.getXAxis().getDisplayPosition(new DateTime(getStartMillis()));
        //position of start and end according to range of axis
        chart.layoutEventBundleNodes(subNodes, 0, chartX);
        super.layoutChildren();
    }

    /**
     * @param w the maximum width the description label should have
     */
    abstract void setDescriptionWidth(double w);

}
