/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2021 Basis Technology Corp.
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
package org.sleuthkit.autopsy.contentviewers;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import static java.util.Objects.nonNull;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.collections.ListChangeListener.Change;
import javafx.concurrent.Task;
import javafx.embed.swing.JFXPanel;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ScrollPane.ScrollBarPolicy;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;
import javax.imageio.ImageIO;
import javax.swing.JFileChooser;
import javafx.scene.Node;
import javax.annotation.concurrent.Immutable;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import org.apache.commons.io.FilenameUtils;
import org.controlsfx.control.MaskerPane;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.actions.GetTagNameAndCommentDialog;
import org.sleuthkit.autopsy.actions.GetTagNameAndCommentDialog.TagNameAndComment;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.casemodule.services.contentviewertags.ContentViewerTagManager;
import org.sleuthkit.autopsy.casemodule.services.contentviewertags.ContentViewerTagManager.ContentViewerTag;
import org.sleuthkit.autopsy.casemodule.services.contentviewertags.ContentViewerTagManager.SerializationException;
import org.sleuthkit.autopsy.contentviewers.imagetagging.ImageTagsUtil;
import org.sleuthkit.autopsy.contentviewers.imagetagging.ImageTagControls;
import org.sleuthkit.autopsy.contentviewers.imagetagging.ImageTagRegion;
import org.sleuthkit.autopsy.contentviewers.imagetagging.ImageTagCreator;
import org.sleuthkit.autopsy.contentviewers.imagetagging.ImageTag;
import org.sleuthkit.autopsy.contentviewers.imagetagging.ImageTagsGroup;
import org.sleuthkit.autopsy.corelibs.OpenCvLoader;
import org.sleuthkit.autopsy.coreutils.ImageUtils;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.datamodel.FileNode;
import org.sleuthkit.autopsy.directorytree.ExternalViewerAction;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.ContentTag;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * A media image file viewer implemented as a Swing panel that uses JavaFX (JFX)
 * components in a child JFX panel to render the image. Images can be zoomed and
 * rotated and a "rubber band box" can be used to select and tag regions.
 */
@NbBundle.Messages({
    "MediaViewImagePanel.externalViewerButton.text=Open in External Viewer  Ctrl+E",
    "MediaViewImagePanel.errorLabel.text=Could not load file into Media View.",
    "MediaViewImagePanel.errorLabel.OOMText=Could not load file into Media View: insufficent memory."
})
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
class MediaViewImagePanel extends JPanel implements MediaFileViewer.MediaViewPanel {

    private static final long serialVersionUID = 1L;
    private static final Logger logger = Logger.getLogger(MediaViewImagePanel.class.getName());
    private static final double[] ZOOM_STEPS = {
        0.0625, 0.125, 0.25, 0.375, 0.5, 0.75,
        1, 1.5, 2, 2.5, 3, 4, 5, 6, 8, 10};
    private static final double MIN_ZOOM_RATIO = 0.0625; // 6.25%
    private static final double MAX_ZOOM_RATIO = 10.0; // 1000%
    private static final Image openInExternalViewerButtonImage = new Image(MediaViewImagePanel.class.getResource("/org/sleuthkit/autopsy/images/external.png").toExternalForm()); //NOI18N
    private final boolean jfxIsInited = org.sleuthkit.autopsy.core.Installer.isJavaFxInited();
    private final PropertyChangeSupport pcs = new PropertyChangeSupport(this);

    /*
     * Threading policy: JFX UI components, must be accessed in JFX thread only.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private final ProgressBar progressBar = new ProgressBar();
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private final MaskerPane maskerPane = new MaskerPane();
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private Group masterGroup;
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private ImageTagsGroup tagsGroup;
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private ImageTagCreator imageTagCreator;
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private ImageView fxImageView;
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private ScrollPane scrollPane;

    /*
     * Threading policy: Swing UI components, must be accessed in EDT only.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private final JPopupMenu imageTaggingOptions;
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private final JMenuItem createTagMenuItem;
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private final JMenuItem deleteTagMenuItem;
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private final JMenuItem hideTagsMenuItem;
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private final JMenuItem exportTagsMenuItem;
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private JFileChooser exportChooser;
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    private final JFXPanel fxPanel;

    /*
     * Panel state variables threading policy:
     *
     * imageFile: The loadFile() method kicks off a JFX background task to read
     * the content of the currently selected file into a JFX Image object. If
     * the task succeeds and is not cancelled, the AbstractFile reference is
     * saved as imageFile. The reference is used for tagging operations which
     * are done in the JFX thread. IMPORTANT: Thread confinement is maintained
     * by capturing the reference in a local variable before dispatching a tag
     * export task to the SwingWorker thread pool. The imageFile field should
     * not be read directly in the JFX thread.
     *
     * readImageFileTask: This is a reference to a JFX background task that
     * reads the content of the currently selected file into a JFX Image object.
     * A reference is maintained so that the task can be cancelled if it is
     * running when the selected image file changes. Only accessed in the JFX
     * thread.
     *
     * imageTransforms: These values are mostly written in the EDT based on user
     * interactions with Swing components and then read in the JFX thread when
     * rendering the image. The exception is recalculation of the zoom ratio
     * based on the image size when a) the selected image file is changed, b)
     * the panel is resized or c) the user pushes the reset button to clear any
     * transforms they have specified. In these three cases, the zoom ratio
     * update happens in the JFX thread since the image must be accessed.
     * IMPORTANT: The image transforms are bundled as atomic state and a
     * snapshot should be captured for each rendering operation on the JFX
     * thread so that the image transforms do not change during rendering due to
     * user interactions in the EDT.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private AbstractFile imageFile;
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private Task<Image> readImageFileTask;
    private volatile ImageTransforms imageTransforms;
    
    // Initializing the JFileChooser in a thread to prevent a block on the EDT
    // see https://stackoverflow.com/questions/49792375/jfilechooser-is-very-slow-when-using-windows-look-and-feel
    private final FutureTask<JFileChooser> futureFileChooser = new FutureTask<>(JFileChooser::new);

    /**
     * Constructs a media image file viewer implemented as a Swing panel that
     * uses JavaFX (JFX) components in a child JFX panel to render the image.
     * Images can be zoomed and rotated and a "rubber band box" can be used to
     * select and tag regions.
     */
    @NbBundle.Messages({
        "MediaViewImagePanel.createTagOption=Create",
        "MediaViewImagePanel.deleteTagOption=Delete",
        "MediaViewImagePanel.hideTagOption=Hide",
        "MediaViewImagePanel.exportTagOption=Export"
    })
    MediaViewImagePanel() {
        initComponents();

        imageTransforms = new ImageTransforms(0, 0, true);
        
        ExecutorService executor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("JFileChooser-background-thread-MediaViewImagePanel").build());
        executor.execute(futureFileChooser);

        //Build popupMenu when Tags Menu button is pressed.
        imageTaggingOptions = new JPopupMenu();
        createTagMenuItem = new JMenuItem(Bundle.MediaViewImagePanel_createTagOption());
        createTagMenuItem.addActionListener((event) -> createTag());
        imageTaggingOptions.add(createTagMenuItem);

        imageTaggingOptions.add(new JSeparator());

        deleteTagMenuItem = new JMenuItem(Bundle.MediaViewImagePanel_deleteTagOption());
        deleteTagMenuItem.addActionListener((event) -> deleteTag());
        imageTaggingOptions.add(deleteTagMenuItem);

        imageTaggingOptions.add(new JSeparator());

        hideTagsMenuItem = new JMenuItem(Bundle.MediaViewImagePanel_hideTagOption());
        hideTagsMenuItem.addActionListener((event) -> showOrHideTags());
        imageTaggingOptions.add(hideTagsMenuItem);

        imageTaggingOptions.add(new JSeparator());

        exportTagsMenuItem = new JMenuItem(Bundle.MediaViewImagePanel_exportTagOption());
        exportTagsMenuItem.addActionListener((event) -> exportTags());
        imageTaggingOptions.add(exportTagsMenuItem);

        imageTaggingOptions.setPopupSize(300, 150);

        //Disable image tagging for non-windows users or upon failure to load OpenCV.
        if (!PlatformUtil.isWindowsOS() || !OpenCvLoader.openCvIsLoaded()) {
            tagsMenu.setEnabled(false);
            imageTaggingOptions.setEnabled(false);
        }

        fxPanel = new JFXPanel();
        if (isInited()) {
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    // build jfx ui (we could do this in FXML?)
                    fxImageView = new ImageView();  // will hold image
                    masterGroup = new Group(fxImageView);
                    tagsGroup = new ImageTagsGroup(fxImageView);
                    tagsGroup.getChildren().addListener((Change<? extends Node> c) -> {
                        if (c.getList().isEmpty()) {
                            pcs.firePropertyChange(new PropertyChangeEvent(this,
                                    "state", null, State.EMPTY));
                        }
                    });

                    /*
                     * RC: I'm not sure exactly why this is located precisely
                     * here. At least putting this call outside of the
                     * constructor avoids leaking the "this" reference of a
                     * partially constructed instance of this class that is
                     * given to the PropertyChangeSupport object created at the
                     * very beginning of construction.
                     */
                    subscribeTagMenuItemsToStateChanges();

                    masterGroup.getChildren().add(tagsGroup);

                    //Update buttons when users select (or unselect) image tags.
                    tagsGroup.addFocusChangeListener((event) -> {
                        if (event.getPropertyName().equals(ImageTagControls.NOT_FOCUSED.getName())) {
                            if (masterGroup.getChildren().contains(imageTagCreator)) {
                                return;
                            }

                            if (tagsGroup.getChildren().isEmpty()) {
                                pcs.firePropertyChange(new PropertyChangeEvent(this,
                                        "state", null, State.EMPTY));
                            } else {
                                pcs.firePropertyChange(new PropertyChangeEvent(this,
                                        "state", null, State.CREATE));
                            }
                        } else if (event.getPropertyName().equals(ImageTagControls.FOCUSED.getName())) {
                            pcs.firePropertyChange(new PropertyChangeEvent(this,
                                    "state", null, State.SELECTED));
                        }
                    });

                    scrollPane = new ScrollPane(masterGroup); // scrolls and sizes imageview
                    scrollPane.getStyleClass().add("bg"); //NOI18N
                    scrollPane.setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
                    scrollPane.setHbarPolicy(ScrollBarPolicy.AS_NEEDED);

                    Scene scene = new Scene(scrollPane); //root of jfx tree
                    scene.getStylesheets().add(MediaViewImagePanel.class.getResource("MediaViewImagePanel.css").toExternalForm()); //NOI18N
                    fxPanel.setScene(scene);

                    fxImageView.setSmooth(true);
                    fxImageView.setCache(true);

                    EventQueue.invokeLater(() -> {
                        add(fxPanel);//add jfx ui to JPanel
                    });
                }
            });
        }
    }

    /**
     * Handle tags menu item enabling and disabling given the state of the
     * content viewer. For example, when the tags group is empty (no tags on
     * image), disable delete menu item, hide menu item, and export menu item.
     */
    private void subscribeTagMenuItemsToStateChanges() {
        pcs.addPropertyChangeListener((event) -> {
            State currentState = (State) event.getNewValue();
            switch (currentState) {
                case CREATE:
                    SwingUtilities.invokeLater(() -> {
                        createTagMenuItem.setEnabled(true);
                        deleteTagMenuItem.setEnabled(false);
                        hideTagsMenuItem.setEnabled(true);
                        exportTagsMenuItem.setEnabled(true);
                    });
                    break;
                case SELECTED:
                    Platform.runLater(() -> {
                        if (masterGroup.getChildren().contains(imageTagCreator)) {
                            imageTagCreator.disconnect();
                            masterGroup.getChildren().remove(imageTagCreator);
                        }
                        SwingUtilities.invokeLater(() -> {
                            createTagMenuItem.setEnabled(false);
                            deleteTagMenuItem.setEnabled(true);
                            hideTagsMenuItem.setEnabled(true);
                            exportTagsMenuItem.setEnabled(true);
                        });
                    });
                    break;
                case HIDDEN:
                    SwingUtilities.invokeLater(() -> {
                        createTagMenuItem.setEnabled(false);
                        deleteTagMenuItem.setEnabled(false);
                        hideTagsMenuItem.setEnabled(true);
                        hideTagsMenuItem.setText(DisplayOptions.SHOW_TAGS.getName());
                        exportTagsMenuItem.setEnabled(false);
                    });
                    break;
                case VISIBLE:
                    SwingUtilities.invokeLater(() -> {
                        createTagMenuItem.setEnabled(true);
                        deleteTagMenuItem.setEnabled(false);
                        hideTagsMenuItem.setEnabled(true);
                        hideTagsMenuItem.setText(DisplayOptions.HIDE_TAGS.getName());
                        exportTagsMenuItem.setEnabled(true);
                    });
                    break;
                case DEFAULT:
                case EMPTY:
                    Platform.runLater(() -> {
                        if (masterGroup.getChildren().contains(imageTagCreator)) {
                            imageTagCreator.disconnect();
                        }
                        SwingUtilities.invokeLater(() -> {
                            createTagMenuItem.setEnabled(true);
                            deleteTagMenuItem.setEnabled(false);
                            hideTagsMenuItem.setEnabled(false);
                            hideTagsMenuItem.setText(DisplayOptions.HIDE_TAGS.getName());
                            exportTagsMenuItem.setEnabled(false);
                        });
                    });
                    break;
                case NONEMPTY:
                    SwingUtilities.invokeLater(() -> {
                        createTagMenuItem.setEnabled(true);
                        deleteTagMenuItem.setEnabled(false);
                        hideTagsMenuItem.setEnabled(true);
                        exportTagsMenuItem.setEnabled(true);
                    });
                    break;
                case DISABLE:
                    SwingUtilities.invokeLater(() -> {
                        createTagMenuItem.setEnabled(false);
                        deleteTagMenuItem.setEnabled(false);
                        hideTagsMenuItem.setEnabled(false);
                        exportTagsMenuItem.setEnabled(false);
                    });
                    break;
                default:
                    break;
            }
        });
    }

    /*
     * Indicates whether or not the panel can be used, i.e., JavaFX has been
     * intitialized.
     */
    final boolean isInited() {
        return jfxIsInited;
    }

    /**
     * Clear the displayed image.
     */
    final void reset() {
        Platform.runLater(() -> {
            fxImageView.setViewport(new Rectangle2D(0, 0, 0, 0));
            fxImageView.setImage(null);
            pcs.firePropertyChange(new PropertyChangeEvent(this,
                    "state", null, State.DEFAULT));
            masterGroup.getChildren().clear();
            scrollPane.setContent(null);
            scrollPane.setContent(masterGroup);
        });
    }

    /**
     * Displays a button with an error message label and a view in external
     * viewer action.
     *
     * @param errorMessage The error message.
     * @param file         The file that could not be viewed.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private void showErrorButton(String errorMessage, AbstractFile file) {
        ensureInJfxThread();
        final Button externalViewerButton = new Button(Bundle.MediaViewImagePanel_externalViewerButton_text(), new ImageView(openInExternalViewerButtonImage));
        externalViewerButton.setOnAction(actionEvent
                -> new ExternalViewerAction(Bundle.MediaViewImagePanel_externalViewerButton_text(), new FileNode(file))
                        .actionPerformed(new ActionEvent(this, ActionEvent.ACTION_PERFORMED, ""))
        );
        final VBox errorNode = new VBox(10, new Label(errorMessage), externalViewerButton);
        errorNode.setAlignment(Pos.CENTER);
    }

    /**
     * Loads an image file into this panel.
     *
     * @param file The image file.
     */
    @ThreadConfined(type = ThreadConfined.ThreadType.AWT)
    final void loadFile(final AbstractFile file) {
        ensureInSwingThread();
        if (!isInited()) {
            return;
        }

        final double panelWidth = fxPanel.getWidth();
        final double panelHeight = fxPanel.getHeight();
        Platform.runLater(() -> {
            /*
             * Set up a new task to get the contents of the image file in
             * displayable form and cancel any previous task in progress.
             */
            if (readImageFileTask != null) {
                readImageFileTask.cancel();
            }
            readImageFileTask = ImageUtils.newReadImageTask(file);
            readImageFileTask.setOnSucceeded(succeeded -> {
                onReadImageTaskSucceeded(file, panelWidth, panelHeight);
            });
            readImageFileTask.setOnFailed(failed -> {
                onReadImageTaskFailed(file);
            });

            /*
             * Update the JFX components to a "task in progress" state and start
             * the task.
             */
            maskerPane.setProgressNode(progressBar);
            progressBar.progressProperty().bind(readImageFileTask.progressProperty());
            maskerPane.textProperty().bind(readImageFileTask.messageProperty());
            scrollPane.setContent(null); // Prevent content display issues.
            scrollPane.setCursor(Cursor.WAIT);
            new Thread(readImageFileTask).start();
        });
    }

    /**
     * Implements a JFX background task state change handler called on read
     * image task success that loads the file content into this panel.
     *
     * @param file        The file.
     * @param panelWidth  The width of the child panel that contains the JFX
     *                    components of this panel.
     * @param panelHeight The height of the child panel that contains the JFX
     *                    components of this panel.
     */
    private void onReadImageTaskSucceeded(AbstractFile file, double panelWidth, double panelHeight) {
        if (!Case.isCaseOpen()) {
            /*
             * Handle the in-between condition when case is being closed and an
             * image was previously selected
             *
             * NOTE: I think this is unnecessary -jm
             */
            reset();
            return;
        }

        Platform.runLater(() -> {
            try {
                Image fxImage = readImageFileTask.get();
                masterGroup.getChildren().clear();
                tagsGroup.getChildren().clear();
                this.imageFile = file;
                if (nonNull(fxImage)) {
                    // We have a non-null image, so let's show it.
                    fxImageView.setImage(fxImage);
                    if (panelWidth != 0 && panelHeight != 0) {
                        resetView(panelWidth, panelHeight);
                    }
                    masterGroup.getChildren().add(fxImageView);
                    masterGroup.getChildren().add(tagsGroup);

                    try {
                        List<ContentTag> tags = Case.getCurrentCase().getServices()
                                .getTagsManager().getContentTagsByContent(file);

                        List<ContentViewerTag<ImageTagRegion>> contentViewerTags = getContentViewerTags(tags);
                        //Add all image tags                            
                        tagsGroup = buildImageTagsGroup(contentViewerTags);
                        if (!tagsGroup.getChildren().isEmpty()) {
                            pcs.firePropertyChange(new PropertyChangeEvent(this,
                                    "state", null, State.NONEMPTY));
                        }
                    } catch (TskCoreException | NoCurrentCaseException ex) {
                        logger.log(Level.WARNING, "Could not retrieve image tags for file in case db", ex); //NON-NLS
                    }
                    scrollPane.setContent(masterGroup);
                } else {
                    showErrorButton(Bundle.MediaViewImagePanel_errorLabel_text(), file);
                }
            } catch (InterruptedException | ExecutionException ex) {
                showErrorButton(Bundle.MediaViewImagePanel_errorLabel_text(), file);
            }
            scrollPane.setCursor(Cursor.DEFAULT);
        });
    }

    /**
     * Implements a JFX background task state change handler called on read
     * image file task failure that displays a button with an error message
     * label and a view in external viewer action.
     *
     * @param file The image file.
     */
    private void onReadImageTaskFailed(AbstractFile file) {
        if (!Case.isCaseOpen()) {
            /*
             * Handle in-between condition when case is being closed and an
             * image was previously selected
             *
             * NOTE: I think this is unnecessary -jm
             */
            reset();
            return;
        }

        Platform.runLater(() -> {
            Throwable exception = readImageFileTask.getException();
            if (exception instanceof OutOfMemoryError
                    && exception.getMessage().contains("Java heap space")) {  //NON-NLS
                showErrorButton(Bundle.MediaViewImagePanel_errorLabel_OOMText(), file);
            } else {
                showErrorButton(Bundle.MediaViewImagePanel_errorLabel_text(), file);
            }

            scrollPane.setCursor(Cursor.DEFAULT);
        });
    }

    /**
     * Finds all ContentViewerTags that are of type 'ImageTagRegion' for the
     * current file.
     *
     * @param contentTags
     *
     * @return
     *
     * @throws TskCoreException
     * @throws NoCurrentCaseException
     */
    private List<ContentViewerTag<ImageTagRegion>> getContentViewerTags(List<ContentTag> contentTags)
            throws TskCoreException, NoCurrentCaseException {
        List<ContentViewerTag<ImageTagRegion>> contentViewerTags = new ArrayList<>();
        for (ContentTag contentTag : contentTags) {
            ContentViewerTag<ImageTagRegion> contentViewerTag = ContentViewerTagManager
                    .getTag(contentTag, ImageTagRegion.class);
            if (contentViewerTag == null) {
                continue;
            }

            contentViewerTags.add(contentViewerTag);
        }
        return contentViewerTags;
    }

    /**
     * Builds ImageTag instances from stored ContentViewerTags of the
     * appropriate type.
     *
     * @param contentTags
     *
     * @return
     *
     * @throws TskCoreException
     * @throws NoCurrentCaseException
     */
    private ImageTagsGroup buildImageTagsGroup(List<ContentViewerTag<ImageTagRegion>> contentViewerTags) {
        ensureInJfxThread();
        contentViewerTags.forEach(contentViewerTag -> {
            /**
             * Build the image tag, add an edit event call back to persist all
             * edits made on this image tag instance.
             */
            tagsGroup.getChildren().add(buildImageTag(contentViewerTag));
        });
        return tagsGroup;
    }

    /**
     * Gets the list of supported MIME types.
     *
     * @return A list of the supported MIME types as Strings.
     */
    @Override
    final public List<String> getSupportedMimeTypes() {
        return Collections.unmodifiableList(Lists.newArrayList(ImageUtils.getSupportedImageMimeTypes()));
    }

    /**
     * Returns supported extensions (each starting with .)
     *
     * @return A unmodifiable list of image extensions as Strings.
     */
    @Override
    final public List<String> getSupportedExtensions() {
        return ImageUtils.getSupportedImageExtensions().stream()
                .map("."::concat) //NOI18N
                .collect(Collectors.toList());
    }

    @Override
    final public boolean isSupported(AbstractFile file) {
        return ImageUtils.isImageThumbnailSupported(file);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        toolbar = new javax.swing.JToolBar();
        rotationTextField = new javax.swing.JTextField();
        rotateLeftButton = new javax.swing.JButton();
        rotateRightButton = new javax.swing.JButton();
        jSeparator1 = new javax.swing.JToolBar.Separator();
        zoomTextField = new javax.swing.JTextField();
        zoomOutButton = new javax.swing.JButton();
        zoomInButton = new javax.swing.JButton();
        jSeparator2 = new javax.swing.JToolBar.Separator();
        zoomResetButton = new javax.swing.JButton();
        filler1 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0));
        filler2 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(32767, 0));
        jPanel1 = new javax.swing.JPanel();
        tagsMenu = new javax.swing.JButton();

        setBackground(new java.awt.Color(0, 0, 0));
        addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentResized(java.awt.event.ComponentEvent evt) {
                formComponentResized(evt);
            }
        });
        setLayout(new javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS));

        toolbar.setFloatable(false);
        toolbar.setRollover(true);
        toolbar.setMaximumSize(new java.awt.Dimension(32767, 23));
        toolbar.setName(""); // NOI18N

        rotationTextField.setEditable(false);
        rotationTextField.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        rotationTextField.setText(org.openide.util.NbBundle.getMessage(MediaViewImagePanel.class, "MediaViewImagePanel.rotationTextField.text")); // NOI18N
        rotationTextField.setMaximumSize(new java.awt.Dimension(50, 2147483647));
        rotationTextField.setMinimumSize(new java.awt.Dimension(50, 20));
        rotationTextField.setPreferredSize(new java.awt.Dimension(50, 20));
        toolbar.add(rotationTextField);

        rotateLeftButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/contentviewers/images/rotate-left.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(rotateLeftButton, org.openide.util.NbBundle.getMessage(MediaViewImagePanel.class, "MediaViewImagePanel.rotateLeftButton.text")); // NOI18N
        rotateLeftButton.setToolTipText(org.openide.util.NbBundle.getMessage(MediaViewImagePanel.class, "MediaViewImagePanel.rotateLeftButton.toolTipText")); // NOI18N
        rotateLeftButton.setFocusable(false);
        rotateLeftButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        rotateLeftButton.setMaximumSize(new java.awt.Dimension(24, 24));
        rotateLeftButton.setMinimumSize(new java.awt.Dimension(24, 24));
        rotateLeftButton.setPreferredSize(new java.awt.Dimension(24, 24));
        rotateLeftButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rotateLeftButtonActionPerformed(evt);
            }
        });
        toolbar.add(rotateLeftButton);

        rotateRightButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/contentviewers/images/rotate-right.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(rotateRightButton, org.openide.util.NbBundle.getMessage(MediaViewImagePanel.class, "MediaViewImagePanel.rotateRightButton.text")); // NOI18N
        rotateRightButton.setFocusable(false);
        rotateRightButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        rotateRightButton.setMaximumSize(new java.awt.Dimension(24, 24));
        rotateRightButton.setMinimumSize(new java.awt.Dimension(24, 24));
        rotateRightButton.setPreferredSize(new java.awt.Dimension(24, 24));
        rotateRightButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                rotateRightButtonActionPerformed(evt);
            }
        });
        toolbar.add(rotateRightButton);

        jSeparator1.setMaximumSize(new java.awt.Dimension(6, 20));
        toolbar.add(jSeparator1);

        zoomTextField.setEditable(false);
        zoomTextField.setHorizontalAlignment(javax.swing.JTextField.RIGHT);
        zoomTextField.setText(org.openide.util.NbBundle.getMessage(MediaViewImagePanel.class, "MediaViewImagePanel.zoomTextField.text")); // NOI18N
        zoomTextField.setMaximumSize(new java.awt.Dimension(50, 2147483647));
        zoomTextField.setMinimumSize(new java.awt.Dimension(50, 20));
        zoomTextField.setPreferredSize(new java.awt.Dimension(50, 20));
        toolbar.add(zoomTextField);

        zoomOutButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/contentviewers/images/zoom-out.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(zoomOutButton, org.openide.util.NbBundle.getMessage(MediaViewImagePanel.class, "MediaViewImagePanel.zoomOutButton.text")); // NOI18N
        zoomOutButton.setFocusable(false);
        zoomOutButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        zoomOutButton.setMaximumSize(new java.awt.Dimension(24, 24));
        zoomOutButton.setMinimumSize(new java.awt.Dimension(24, 24));
        zoomOutButton.setPreferredSize(new java.awt.Dimension(24, 24));
        zoomOutButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zoomOutButtonActionPerformed(evt);
            }
        });
        toolbar.add(zoomOutButton);

        zoomInButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/contentviewers/images/zoom-in.png"))); // NOI18N
        org.openide.awt.Mnemonics.setLocalizedText(zoomInButton, org.openide.util.NbBundle.getMessage(MediaViewImagePanel.class, "MediaViewImagePanel.zoomInButton.text")); // NOI18N
        zoomInButton.setFocusable(false);
        zoomInButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        zoomInButton.setMaximumSize(new java.awt.Dimension(24, 24));
        zoomInButton.setMinimumSize(new java.awt.Dimension(24, 24));
        zoomInButton.setPreferredSize(new java.awt.Dimension(24, 24));
        zoomInButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zoomInButtonActionPerformed(evt);
            }
        });
        toolbar.add(zoomInButton);

        jSeparator2.setMaximumSize(new java.awt.Dimension(6, 20));
        toolbar.add(jSeparator2);

        org.openide.awt.Mnemonics.setLocalizedText(zoomResetButton, org.openide.util.NbBundle.getMessage(MediaViewImagePanel.class, "MediaViewImagePanel.zoomResetButton.text")); // NOI18N
        zoomResetButton.setFocusable(false);
        zoomResetButton.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        zoomResetButton.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        zoomResetButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                zoomResetButtonActionPerformed(evt);
            }
        });
        toolbar.add(zoomResetButton);
        toolbar.add(filler1);
        toolbar.add(filler2);
        toolbar.add(jPanel1);

        org.openide.awt.Mnemonics.setLocalizedText(tagsMenu, org.openide.util.NbBundle.getMessage(MediaViewImagePanel.class, "MediaViewImagePanel.tagsMenu.text_1")); // NOI18N
        tagsMenu.setFocusable(false);
        tagsMenu.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        tagsMenu.setMaximumSize(new java.awt.Dimension(75, 21));
        tagsMenu.setMinimumSize(new java.awt.Dimension(75, 21));
        tagsMenu.setPreferredSize(new java.awt.Dimension(75, 21));
        tagsMenu.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        tagsMenu.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                tagsMenuMousePressed(evt);
            }
        });
        toolbar.add(tagsMenu);

        add(toolbar);
    }// </editor-fold>//GEN-END:initComponents

    private void rotateLeftButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rotateLeftButtonActionPerformed
        rotateImage(270);
    }//GEN-LAST:event_rotateLeftButtonActionPerformed

    private void rotateRightButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_rotateRightButtonActionPerformed
        rotateImage(90);
    }//GEN-LAST:event_rotateRightButtonActionPerformed

    private void rotateImage(int angle) {
        final double panelWidth = fxPanel.getWidth();
        final double panelHeight = fxPanel.getHeight();
        ImageTransforms currentTransforms = imageTransforms;
        double newRotation = (currentTransforms.getRotation() + angle) % 360;
        final ImageTransforms newTransforms = new ImageTransforms(currentTransforms.getZoomRatio(), newRotation, false);
        imageTransforms = newTransforms;
        Platform.runLater(() -> {
            updateView(panelWidth, panelHeight, newTransforms);
        });
    }

    private void zoomInButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoomInButtonActionPerformed
        zoomImage(ZoomDirection.IN);
    }//GEN-LAST:event_zoomInButtonActionPerformed

    private void zoomOutButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoomOutButtonActionPerformed
        zoomImage(ZoomDirection.OUT);
    }//GEN-LAST:event_zoomOutButtonActionPerformed

    private void zoomImage(ZoomDirection direction) {
        ensureInSwingThread();
        final double panelWidth = fxPanel.getWidth();
        final double panelHeight = fxPanel.getHeight();
        final ImageTransforms currentTransforms = imageTransforms;
        double newZoomRatio;
        if (direction == ZoomDirection.IN) {
            newZoomRatio = zoomImageIn(currentTransforms.getZoomRatio());
        } else {
            newZoomRatio = zoomImageOut(currentTransforms.getZoomRatio());
        }
        final ImageTransforms newTransforms = new ImageTransforms(newZoomRatio, currentTransforms.getRotation(), false);
        imageTransforms = newTransforms;
        Platform.runLater(() -> {
            updateView(panelWidth, panelHeight, newTransforms);
        });
    }

    private double zoomImageIn(double zoomRatio) {
        double newZoomRatio = zoomRatio;
        for (int i = 0; i < ZOOM_STEPS.length; i++) {
            if (newZoomRatio < ZOOM_STEPS[i]) {
                newZoomRatio = ZOOM_STEPS[i];
                break;
            }
        }
        return newZoomRatio;
    }

    private double zoomImageOut(double zoomRatio) {
        double newZoomRatio = zoomRatio;
        for (int i = ZOOM_STEPS.length - 1; i >= 0; i--) {
            if (newZoomRatio > ZOOM_STEPS[i]) {
                newZoomRatio = ZOOM_STEPS[i];
                break;
            }
        }
        return newZoomRatio;
    }

    private void zoomResetButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_zoomResetButtonActionPerformed
        final ImageTransforms currentTransforms = imageTransforms;
        final ImageTransforms newTransforms = new ImageTransforms(0, currentTransforms.getRotation(), true);
        imageTransforms = newTransforms;
        resetView();
    }//GEN-LAST:event_zoomResetButtonActionPerformed

    private void formComponentResized(java.awt.event.ComponentEvent evt) {//GEN-FIRST:event_formComponentResized
        final ImageTransforms currentTransforms = imageTransforms;
        if (currentTransforms.shouldAutoResize()) {
            resetView();
        } else {
            final double panelWidth = fxPanel.getWidth();
            final double panelHeight = fxPanel.getHeight();
            Platform.runLater(() -> {
                updateView(panelWidth, panelHeight, currentTransforms);
            });
        }
    }//GEN-LAST:event_formComponentResized

    /**
     * Deletes the selected tag when the Delete button is pressed in the Tag
     * Menu.
     */
    private void deleteTag() {
        Platform.runLater(() -> {
            ImageTag tagInFocus = tagsGroup.getFocus();
            if (tagInFocus == null) {
                return;
            }

            try {
                ContentViewerTag<ImageTagRegion> contentViewerTag = tagInFocus.getContentViewerTag();
                scrollPane.setCursor(Cursor.WAIT);
                ContentViewerTagManager.deleteTag(contentViewerTag);
                Case.getCurrentCase().getServices().getTagsManager().deleteContentTag(contentViewerTag.getContentTag());
                tagsGroup.getChildren().remove(tagInFocus);
            } catch (TskCoreException | NoCurrentCaseException ex) {
                logger.log(Level.WARNING, "Could not delete image tag in case db", ex); //NON-NLS
            }

            scrollPane.setCursor(Cursor.DEFAULT);
        });

        pcs.firePropertyChange(new PropertyChangeEvent(this,
                "state", null, State.CREATE));
    }

    /**
     * Enables create tag logic when the Create button is pressed in the Tags
     * Menu.
     */
    private void createTag() {
        pcs.firePropertyChange(new PropertyChangeEvent(this,
                "state", null, State.DISABLE));
        Platform.runLater(() -> {
            imageTagCreator = new ImageTagCreator(fxImageView);

            PropertyChangeListener newTagListener = (event) -> {
                SwingUtilities.invokeLater(() -> {
                    ImageTagRegion tag = (ImageTagRegion) event.getNewValue();
                    //Ask the user for tag name and comment
                    TagNameAndComment result = GetTagNameAndCommentDialog.doDialog();
                    if (result != null) {
                        //Persist and build image tag
                        Platform.runLater(() -> {
                            try {
                                scrollPane.setCursor(Cursor.WAIT);
                                ContentViewerTag<ImageTagRegion> contentViewerTag = storeImageTag(tag, result);
                                ImageTag imageTag = buildImageTag(contentViewerTag);
                                tagsGroup.getChildren().add(imageTag);
                            } catch (TskCoreException | SerializationException | NoCurrentCaseException ex) {
                                logger.log(Level.WARNING, "Could not save new image tag in case db", ex); //NON-NLS
                            }

                            scrollPane.setCursor(Cursor.DEFAULT);
                        });
                    }

                    pcs.firePropertyChange(new PropertyChangeEvent(this,
                            "state", null, State.CREATE));
                });

                //Remove image tag creator from panel
                Platform.runLater(() -> {
                    imageTagCreator.disconnect();
                    masterGroup.getChildren().remove(imageTagCreator);
                });
            };

            imageTagCreator.addNewTagListener(newTagListener);
            masterGroup.getChildren().add(imageTagCreator);
        });
    }

    /**
     * Creates an ImageTag instance from the ContentViewerTag.
     *
     * @param contentViewerTag
     *
     * @return
     */
    private ImageTag buildImageTag(ContentViewerTag<ImageTagRegion> contentViewerTag) {
        ensureInJfxThread();
        ImageTag imageTag = new ImageTag(contentViewerTag, fxImageView);

        //Automatically persist edits made by user
        imageTag.subscribeToEditEvents((edit) -> {
            try {
                scrollPane.setCursor(Cursor.WAIT);
                ImageTagRegion newRegion = (ImageTagRegion) edit.getNewValue();
                ContentViewerTagManager.updateTag(contentViewerTag, newRegion);
            } catch (SerializationException | TskCoreException | NoCurrentCaseException ex) {
                logger.log(Level.WARNING, "Could not save edit for image tag in case db", ex); //NON-NLS
            }
            scrollPane.setCursor(Cursor.DEFAULT);
        });
        return imageTag;
    }

    /**
     * Stores the image tag by creating a ContentTag instance and associating
     * the ImageTagRegion data with it in the case database.
     *
     * @param data
     * @param result
     */
    private ContentViewerTag<ImageTagRegion> storeImageTag(ImageTagRegion data, TagNameAndComment result) throws TskCoreException, SerializationException, NoCurrentCaseException {
        ensureInJfxThread();
        scrollPane.setCursor(Cursor.WAIT);
        try {
            ContentTag contentTag = Case.getCurrentCaseThrows().getServices().getTagsManager()
                    .addContentTag(imageFile, result.getTagName(), result.getComment());
            return ContentViewerTagManager.saveTag(contentTag, data);
        } finally {
            scrollPane.setCursor(Cursor.DEFAULT);
        }
    }

    /**
     * Hides or show tags when the Hide or Show button is pressed in the Tags
     * Menu.
     */
    private void showOrHideTags() {
        Platform.runLater(() -> {
            if (DisplayOptions.HIDE_TAGS.getName().equals(hideTagsMenuItem.getText())) {
                //Temporarily remove the tags group and update buttons
                masterGroup.getChildren().remove(tagsGroup);
                hideTagsMenuItem.setText(DisplayOptions.SHOW_TAGS.getName());
                tagsGroup.clearFocus();
                pcs.firePropertyChange(new PropertyChangeEvent(this,
                        "state", null, State.HIDDEN));
            } else {
                //Add tags group back in and update buttons
                masterGroup.getChildren().add(tagsGroup);
                hideTagsMenuItem.setText(DisplayOptions.HIDE_TAGS.getName());
                pcs.firePropertyChange(new PropertyChangeEvent(this,
                        "state", null, State.VISIBLE));
            }
        });
    }

    @NbBundle.Messages({
        "MediaViewImagePanel.exportSaveText=Save",
        "MediaViewImagePanel.successfulExport=Tagged image was successfully saved.",
        "MediaViewImagePanel.unsuccessfulExport=Unable to export tagged image to disk.",
        "MediaViewImagePanel.fileChooserTitle=Choose a save location"
    })
    private void exportTags() {
        Platform.runLater(() -> {
            final AbstractFile file = imageFile;
            tagsGroup.clearFocus();
            SwingUtilities.invokeLater(() -> {
                
                if(exportChooser == null) {
                    try {
                        exportChooser = futureFileChooser.get();
                    } catch (InterruptedException | ExecutionException ex) {
                        // If something happened with the thread try and 
                        // initalized the chooser now
                        logger.log(Level.WARNING, "A failure occurred in the JFileChooser background thread");
                        exportChooser = new JFileChooser();
                    } 
                }
                
                exportChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                //Always base chooser location to export folder
                exportChooser.setCurrentDirectory(new File(Case.getCurrentCase().getExportDirectory()));
                int returnVal = exportChooser.showDialog(this, Bundle.MediaViewImagePanel_exportSaveText());
                if (returnVal == JFileChooser.APPROVE_OPTION) {
                    new SwingWorker<Void, Void>() {
                        @Override
                        protected Void doInBackground() {
                            try {
                                //Retrieve content viewer tags
                                List<ContentTag> tags = Case.getCurrentCase().getServices()
                                        .getTagsManager().getContentTagsByContent(file);
                                List<ContentViewerTag<ImageTagRegion>> contentViewerTags = getContentViewerTags(tags);

                                //Pull out image tag regions
                                Collection<ImageTagRegion> regions = contentViewerTags.stream()
                                        .map(cvTag -> cvTag.getDetails()).collect(Collectors.toList());

                                //Apply tags to image and write to file
                                BufferedImage taggedImage = ImageTagsUtil.getImageWithTags(file, regions);
                                Path output = Paths.get(exportChooser.getSelectedFile().getPath(),
                                        FilenameUtils.getBaseName(file.getName()) + "-with_tags.png"); //NON-NLS
                                ImageIO.write(taggedImage, "png", output.toFile());

                                JOptionPane.showMessageDialog(null, Bundle.MediaViewImagePanel_successfulExport());
                            } catch (Exception ex) { //Runtime exceptions may spill out of ImageTagsUtil from JavaFX.
                                //This ensures we (devs and users) have something when it doesn't work.
                                logger.log(Level.WARNING, "Unable to export tagged image to disk", ex); //NON-NLS
                                JOptionPane.showMessageDialog(null, Bundle.MediaViewImagePanel_unsuccessfulExport());
                            }
                            return null;
                        }
                    }.execute();
                }
            });
        });
    }

    private void tagsMenuMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_tagsMenuMousePressed
        if (imageTaggingOptions.isEnabled()) {
            imageTaggingOptions.show(tagsMenu, -300 + tagsMenu.getWidth(), tagsMenu.getHeight() + 3);
        }
    }//GEN-LAST:event_tagsMenuMousePressed

    /**
     * Display states for the show/hide tags button.
     */
    private enum DisplayOptions {
        HIDE_TAGS("Hide"),
        SHOW_TAGS("Show");

        private final String name;

        DisplayOptions(String name) {
            this.name = name;
        }

        String getName() {
            return name;
        }
    }

    /**
     * Different states that the content viewer can be in. These states drive
     * which buttons are enabled for tagging.
     */
    private enum State {
        HIDDEN,
        VISIBLE,
        SELECTED,
        CREATE,
        EMPTY,
        NONEMPTY,
        DEFAULT,
        DISABLE;
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.Box.Filler filler1;
    private javax.swing.Box.Filler filler2;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JToolBar.Separator jSeparator1;
    private javax.swing.JToolBar.Separator jSeparator2;
    private javax.swing.JButton rotateLeftButton;
    private javax.swing.JButton rotateRightButton;
    private javax.swing.JTextField rotationTextField;
    private javax.swing.JButton tagsMenu;
    private javax.swing.JToolBar toolbar;
    private javax.swing.JButton zoomInButton;
    private javax.swing.JButton zoomOutButton;
    private javax.swing.JButton zoomResetButton;
    private javax.swing.JTextField zoomTextField;
    // End of variables declaration//GEN-END:variables

    /**
     * Gets the dimensions of the Swing container of the JFX components and then
     * resets the components used to display the image to their default state.
     */
    private void resetView() {
        ensureInSwingThread();
        final double panelWidth = fxPanel.getWidth();
        final double panelHeight = fxPanel.getHeight();
        Platform.runLater(() -> {
            resetView(panelWidth, panelHeight);
        });
    }

    /**
     * Resets the zoom and rotation values to their defaults. The zoom level
     * gets defaulted to the current size of the panel. The rotation will be set
     * to zero.
     *
     * @param panelWidth  The width of the child panel that contains the JFX
     *                    components of this panel.
     * @param panelHeight The height of the child panel that contains the JFX
     *                    components of this panel.
     */
    private void resetView(double panelWidth, double panelHeight) {
        ensureInJfxThread();

        Image image = fxImageView.getImage();
        if (image == null) {
            return;
        }

        double imageWidth = image.getWidth();
        double imageHeight = image.getHeight();
        double scrollPaneWidth = panelWidth;
        double scrollPaneHeight = panelHeight;
        double zoomRatioWidth = scrollPaneWidth / imageWidth;
        double zoomRatioHeight = scrollPaneHeight / imageHeight;
        double newZoomRatio = zoomRatioWidth < zoomRatioHeight ? zoomRatioWidth : zoomRatioHeight; // Use the smallest ratio size to fit the entire image in the view area.
        final ImageTransforms newTransforms = new ImageTransforms(newZoomRatio, 0, true);
        imageTransforms = newTransforms;

        scrollPane.setHvalue(0);
        scrollPane.setVvalue(0);

        updateView(panelWidth, panelHeight, newTransforms);
    }

    /**
     * Updates the display of the image to use the current zoom and rotation
     * values.
     *
     * Note: For zoom levels less than 100%, special accomodations are made in
     * order to keep the image fully visible. This is because the viewport size
     * change occurs before any transforms execute, thus chopping off part of
     * the image. So the viewport is kept the same size. Scrolling adjustments
     * are also made to try and ensure when the user zooms out, they don't find
     * themselves looking at an entire screen of dead space.
     *
     * @param panelWidth  The width of the child panel that contains the JFX
     *                    components of this panel.
     * @param panelHeight The height of the child panel that contains the JFX
     *                    components of this panel.
     */
    /**
     *
     * @param panelWidth
     * @param panelHeight
     * @param imageTransforms
     */
    private void updateView(double panelWidth, double panelHeight, ImageTransforms imageTransforms) {
        ensureInJfxThread();
        Image image = fxImageView.getImage();
        if (image == null) {
            return;
        }

        // Image dimensions
        double imageWidth = image.getWidth();
        double imageHeight = image.getHeight();

        // Image dimensions with zooming applied
        double currentZoomRatio = imageTransforms.getZoomRatio();
        double adjustedImageWidth = imageWidth * currentZoomRatio;
        double adjustedImageHeight = imageHeight * currentZoomRatio;

        // ImageView viewport dimensions
        double viewportWidth;
        double viewportHeight;

        // Coordinates to center the image on the panel
        double centerOffsetX = (panelWidth / 2) - (imageWidth / 2);
        double centerOffsetY = (panelHeight / 2) - (imageHeight / 2);

        // Coordinates to keep the image inside the left/top boundaries
        double leftOffsetX;
        double topOffsetY;

        // Scroll bar positions
        double scrollX = scrollPane.getHvalue();
        double scrollY = scrollPane.getVvalue();

        // Scroll bar position boundaries (work-around for viewport size bug)
        double maxScrollX;
        double maxScrollY;

        // Set viewport size and translation offsets.
        final double currentRotation = imageTransforms.getRotation();
        if ((currentRotation % 180) == 0) {
            // Rotation is 0 or 180.
            viewportWidth = adjustedImageWidth;
            viewportHeight = adjustedImageHeight;
            leftOffsetX = (adjustedImageWidth - imageWidth) / 2;
            topOffsetY = (adjustedImageHeight - imageHeight) / 2;
            maxScrollX = (adjustedImageWidth - panelWidth) / (imageWidth - panelWidth);
            maxScrollY = (adjustedImageHeight - panelHeight) / (imageHeight - panelHeight);
        } else {
            // Rotation is 90 or 270.
            viewportWidth = adjustedImageHeight;
            viewportHeight = adjustedImageWidth;
            leftOffsetX = (adjustedImageHeight - imageWidth) / 2;
            topOffsetY = (adjustedImageWidth - imageHeight) / 2;
            maxScrollX = (adjustedImageHeight - panelWidth) / (imageWidth - panelWidth);
            maxScrollY = (adjustedImageWidth - panelHeight) / (imageHeight - panelHeight);
        }

        // Work around bug that truncates image if dimensions are too small.
        if (viewportWidth < imageWidth) {
            viewportWidth = imageWidth;
            if (scrollX > maxScrollX) {
                scrollX = maxScrollX;
            }
        }
        if (viewportHeight < imageHeight) {
            viewportHeight = imageHeight;
            if (scrollY > maxScrollY) {
                scrollY = maxScrollY;
            }
        }

        // Update the viewport size.
        fxImageView.setViewport(new Rectangle2D(
                0, 0, viewportWidth, viewportHeight));

        // Step 1: Zoom
        Scale scale = new Scale();
        scale.setX(currentZoomRatio);
        scale.setY(currentZoomRatio);
        scale.setPivotX(imageWidth / 2);
        scale.setPivotY(imageHeight / 2);

        // Step 2: Rotate
        Rotate rotate = new Rotate();
        rotate.setPivotX(imageWidth / 2);
        rotate.setPivotY(imageHeight / 2);
        rotate.setAngle(currentRotation);

        // Step 3: Position
        Translate translate = new Translate();
        translate.setX(viewportWidth > fxPanel.getWidth() ? leftOffsetX : centerOffsetX);
        translate.setY(viewportHeight > fxPanel.getHeight() ? topOffsetY : centerOffsetY);

        // Add the transforms in reverse order of intended execution.
        // Note: They MUST be added in this order to ensure translate is
        // executed last.
        masterGroup.getTransforms().clear();
        masterGroup.getTransforms().addAll(translate, rotate, scale);

        // Adjust scroll bar positions for view changes.
        if (viewportWidth > fxPanel.getWidth()) {
            scrollPane.setHvalue(scrollX);
        }
        if (viewportHeight > fxPanel.getHeight()) {
            scrollPane.setVvalue(scrollY);
        }

        /*
         * RC: There is a race condition here, but it will probably be corrected
         * so fast the user will never see it. See Jira-6848 for details and a
         * solution that will simplify this class greatly in terms of thread
         * safety.
         */
        SwingUtilities.invokeLater(() -> {
            // Update all image controls to reflect the current values.
            zoomOutButton.setEnabled(currentZoomRatio > MIN_ZOOM_RATIO);
            zoomInButton.setEnabled(currentZoomRatio < MAX_ZOOM_RATIO);
            rotationTextField.setText((int) currentRotation + "°");
            zoomTextField.setText((Math.round(currentZoomRatio * 100.0)) + "%");
        });
    }

    /**
     * Checks that the calling code is running in the JFX thread and throws an
     * IllegalStateException if it is not. The intent of this method is to make
     * thread confinement errors obvious at development time.
     */
    private void ensureInJfxThread() {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("Attempt to execute JFX code outside of JFX thread"); //NON-NLS
        }
    }

    /**
     * Checks that the calling code is running in the JFX thread and throws an
     * IllegalStateException if it is not. The intent of this method is to make
     * thread confinement errors obvious at development time.
     */
    private void ensureInSwingThread() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Attempt to execute Swing code outside of EDT"); //NON-NLS
        }
    }

    /**
     * Used to idicate zoom direction.
     */
    private enum ZoomDirection {
        IN, OUT
    };

    /**
     * Records a snapshot of the image transforms specified by the user.
     */
    @Immutable
    private static class ImageTransforms {

        private final double zoomRatio;
        private final double rotation;
        private final boolean autoResize;

        ImageTransforms(double zoomRatio, double rotation, boolean autoResize) {
            this.zoomRatio = zoomRatio;
            this.rotation = rotation;
            this.autoResize = autoResize;
        }

        /**
         * Gets the current zoom ratio.
         *
         * @return The zoom ratio.
         */
        private double getZoomRatio() {
            return zoomRatio;
        }

        /**
         * Gets the current image rotation value. Can be 0, 90, 180, or 270
         * degrees.
         *
         * @return The rotaion, in degrees.
         */
        private double getRotation() {
            return rotation;
        }

        /**
         * Indicates whether or not auto resizing is in effect when the user
         * resizes the panel. Should always be true unless the user has used the
         * zoom buttons.
         *
         * @return True or false.
         */
        private boolean shouldAutoResize() {
            return autoResize;
        }

    }

}
