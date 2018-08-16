/*
 * Autopsy Forensic Browser
 *
 * Copyright 2015 Basis Technology Corp.
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

import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import java.util.Objects;
import static java.util.Objects.nonNull;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
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
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.imagegallery.ImageGalleryController;
import org.sleuthkit.autopsy.imagegallery.actions.OpenExternalViewerAction;
import org.sleuthkit.autopsy.imagegallery.datamodel.DrawableFile;

/**
 * Abstract base class for views of a single drawable file.
 */
@NbBundle.Messages({"DrawableUIBase.errorLabel.text=Could not read file",
    "DrawableUIBase.errorLabel.OOMText=Insufficent memory"})
abstract public class DrawableUIBase extends AnchorPane implements DrawableView {

    private static final Logger LOGGER = Logger.getLogger(DrawableUIBase.class.getName());

    static final ListeningExecutorService exec = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor());

    @FXML
    BorderPane imageBorder;
    @FXML
    ImageView imageView;

    private final ImageGalleryController controller;

    private FileFuture fileFuture = new FileFuture(null);

    public final class FileFuture extends AbstractFuture<Optional<DrawableFile>> {

        private final Long fileID;

        public Long getFileID() {
            return fileID;
        }

        FileFuture(Long fileID) {
            this.fileID = fileID;
            if (fileID != null) {
                setFuture(exec.submit(() -> Optional.ofNullable(getController().getFileFromId(fileIDOpt.get()))));
            } else {
                setFuture(Futures.immediateFuture(Optional.empty()));
            }
        }

        public void addFXListener(Consumer<Optional<DrawableFile>> listener) {
            super.addListener(() -> {
                try {
                    listener.accept(get());
                } catch (InterruptedException ex) {
                    Exceptions.printStackTrace(ex);
                } catch (ExecutionException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }, new FXExecutor()); //To change body of generated methods, choose Tools | Templates.
        }

    }

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

    synchronized void setFileFuture(FileFuture fileOpt) {
        this.fileFuture = fileOpt;
    }

    @Override
    synchronized public FileFuture getFile() {
        if (fileIDOpt.isPresent()) {
            if (Objects.equals(fileFuture.getFileID(), fileIDOpt.get())) {
                return fileFuture;
            } else {
                fileFuture = new FileFuture(fileIDOpt.get());
                return fileFuture;
            }
        } else {
            return new FileFuture(null);
        }
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

        getFile().addListener(() -> {
            try {
                if (getFile().get().isPresent()) {
                    doReadImageTask(getFile().get().get());
                }
            } catch (InterruptedException ex) {
                Exceptions.printStackTrace(ex);
            } catch (ExecutionException ex) {
                Exceptions.printStackTrace(ex);
            }
        }, exec);

    }

    synchronized Node doReadImageTask(DrawableFile file) {
        Task<Image> myTask = newReadImageTask(file);
        imageTask = myTask;
        Node progressNode = newProgressIndicator(myTask);
        Platform.runLater(() -> imageBorder.setCenter(progressNode));

        //called on fx thread
        myTask.setOnSucceeded(succeeded -> {
            showImage(file, myTask);
            synchronized (DrawableUIBase.this) {
                imageTask = null;
            }
        });
        myTask.setOnFailed(failed -> {
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
        myTask.setOnCancelled(cancelled -> {
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
     *
     * @param file      the value of file
     * @param imageTask the value of imageTask
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
