/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015-18 Basis Technology Corp.
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
package org.sleuthkit.autopsy.imagegallery.gui.drawableviews;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.util.Objects;
import static java.util.Objects.nonNull;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import org.controlsfx.control.action.ActionUtils;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.actions.OpenExternalViewerAction;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Abstract base class for views of a single drawable file.
 */
@NbBundle.Messages({"DrawableUIBase.errorLabel.text=Could not read file",
    "DrawableUIBase.errorLabel.OOMText=Insufficent memory"})
abstract public class DrawableUIBase extends AnchorPane implements DrawableView {

    /** The use of SingleThreadExecutor means we can only load a single image at
     * a time */
    static final Executor exec = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("DrawableUIBase-%d").build());

    @FXML
    BorderPane imageBorder;
    @FXML
    ImageView imageView;

    private final ImageGalleryController controller;

    private Optional<DrawableFile> fileOpt = Optional.empty();

    private Optional<Long> fileIDOpt = Optional.empty();
    private volatile Task<Image> imageTask;

    public DrawableUIBase(ImageGalleryController controller) {
        this.controller = controller;
    }

    @Override
    public ImageGalleryController getController() {
        return controller;
    }

    @Override
    public Optional<Long> getFileID() {
        return fileIDOpt;
    }

    synchronized void setFileIDOpt(Optional<Long> fileIDOpt) {
        this.fileIDOpt = fileIDOpt;
    }

    synchronized void setFileOpt(Optional<DrawableFile> fileOpt) {
        this.fileOpt = fileOpt;
    }

    @Override
    synchronized public Optional<DrawableFile> getFile() {
        if (fileIDOpt.isPresent()) {
            if (!fileOpt.isPresent() || fileOpt.get().getId() != fileIDOpt.get()) {
                try {
                    fileOpt = Optional.ofNullable(getController().getFileFromID(fileIDOpt.get()));
                } catch (TskCoreException ex) {
                    fileOpt = Optional.empty();
                }
            }
        } else {
            fileOpt = Optional.empty();
        }
        return fileOpt;
    }

    protected abstract void setFileHelper(Long newFileID);

    @Override
    synchronized public void setFile(Long newFileID) {
        if (getFileID().isPresent()) {
            if (Objects.equals(newFileID, getFileID().get()) == false) {
                setFileHelper(newFileID);
            }
        } else {
            setFileHelper(newFileID);
        }
    }

    synchronized protected void updateContent() {
        getFile().ifPresent(this::doReadImageTask);
    }

    synchronized Node doReadImageTask(DrawableFile file) {
        Task<Image> myTask = newReadImageTask(file);
        imageTask = myTask;
        Node progressNode = newProgressIndicator(myTask);
        Platform.runLater(() -> imageBorder.setCenter(progressNode));

        //called on fx thread
        myTask.setOnSucceeded(succeeded -> {            //on fx thread
            showImage(file, myTask);
            synchronized (DrawableUIBase.this) {
                imageTask = null;
            }
        });
        myTask.setOnFailed(failed -> {            //on fx thread
            Throwable exception = myTask.getException();
            if (exception instanceof OutOfMemoryError
                && exception.getMessage().contains("Java heap space")) { //NON-NLS
                showErrorNode(Bundle.DrawableUIBase_errorLabel_OOMText(), file);
            } else {
                showErrorNode(Bundle.DrawableUIBase_errorLabel_text(), file);
            }
            synchronized (DrawableUIBase.this) {
                imageTask = null;
            }
        });
        myTask.setOnCancelled(cancelled -> {            //on fx thread
            synchronized (DrawableUIBase.this) {
                imageTask = null;
            }
            imageView.setImage(null);
            imageBorder.setCenter(null);
        });

        exec.execute(myTask);
        return progressNode;
    }

    synchronized protected void disposeContent() {
        if (imageTask != null) {
            imageTask.cancel();
        }
        imageTask = null;
        Platform.runLater(() -> {
            imageView.setImage(null);
            imageBorder.setCenter(null);
        });
    }

    /**
     * Get a new progress indicator to use as a place holder for the image in
     * this view.
     *
     * @param imageTask The imageTask to get a progress indicator for.
     *
     * @return The new Node to use as a progress indicator.
     */
    Node newProgressIndicator(final Task<?> imageTask) {
        ProgressIndicator loadingProgressIndicator = new ProgressIndicator(-1);
        loadingProgressIndicator.progressProperty().bind(imageTask.progressProperty());
        return loadingProgressIndicator;
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    private void showImage(DrawableFile file, Task<Image> imageTask) {
        //Note that all error conditions are allready logged in readImageTask.succeeded()
        try {
            Image fxImage = imageTask.get();
            if (nonNull(fxImage)) {
                //we have non-null image show it
                imageView.setImage(fxImage);
                imageBorder.setCenter(imageView);
            } else {
                showErrorNode(Bundle.DrawableUIBase_errorLabel_text(), file);
            }
        } catch (CancellationException ex) {

        } catch (InterruptedException | ExecutionException ex) {
            showErrorNode(Bundle.DrawableUIBase_errorLabel_text(), file);
        }
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    void showErrorNode(String errorMessage, DrawableFile file) {
        Button createButton = ActionUtils.createButton(new OpenExternalViewerAction(file));

        VBox vBox = new VBox(10, new Label(errorMessage), createButton);
        vBox.setAlignment(Pos.CENTER);
        imageBorder.setCenter(vBox);
    }

    abstract Task<Image> newReadImageTask(DrawableFile file);
}
