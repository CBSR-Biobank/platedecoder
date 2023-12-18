package org.biobank.platedecoder.ui.scene;

import static org.biobank.platedecoder.ui.JavaFxHelper.createButton;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.biobank.platedecoder.dmscanlib.DecodeOptions;
import org.biobank.platedecoder.dmscanlib.DecodeResult;
import org.biobank.platedecoder.dmscanlib.DecodedWell;
import org.biobank.platedecoder.dmscanlib.ScanLibResult;
import org.biobank.platedecoder.model.ImageSource;
import org.biobank.platedecoder.model.Plate;
import org.biobank.platedecoder.model.PlateDecoderPreferences;
import org.biobank.platedecoder.service.DecodeImageTask;
import org.biobank.platedecoder.ui.BarcodePositionChooser;
import org.biobank.platedecoder.ui.JavaFxHelper;
import org.biobank.platedecoder.ui.ManualDecodeDialog;
import org.biobank.platedecoder.ui.PlateOrientationChooser;
import org.biobank.platedecoder.ui.PlateTypeChooser;
import org.biobank.platedecoder.ui.wellgrid.WellCell;
import org.biobank.platedecoder.ui.wellgrid.WellGrid;
import org.biobank.platedecoder.ui.wellgrid.WellGridHandler;
import org.controlsfx.dialog.ProgressDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.shape.Rectangle;

/**
 * Used for decoding the tubes present in an image.
 */
public class DecodeImageScene extends SceneRoot implements WellGridHandler {

   // @SuppressWarnings("unused")
   private static final Logger LOG = LoggerFactory.getLogger(DecodeImageScene.class);

   private static final String TITLE_AREA_MESSAGE =
      "Align grid so that each cell contains a 2D barcode, and then press the Decode button. "
      + "If there are missed cells, double click one to decode it with a hand held scanner. "
      + "Press the Next button once all cells are decoded.";

   private ImageView imageView;

   private ImageSource imageSource;

   private Group imageGroup;

   private ScrollPane imagePane;

   private WellGrid wellGrid;

   private Label filenameLabel;

   public DecodeImageScene() {
      super("Align grid with barcodes");
      setTitleAreaMessage(TITLE_AREA_MESSAGE);
      createWellGrid();

      model.getPlateTypeProperty().addListener((observable, oldValue, newValue) -> {
            createWellGrid();
         });

      model.getPlateOrientationProperty().addListener((observable, oldValue, newValue) -> {
            createWellGrid();
         });

      model.getBarcodePositionProperty().addListener((observable, oldValue, newValue) -> {
            createWellGrid();
         });
   }

   @Override
   public void onDisplay() {
      // NOTE: could be called with model.getPlate() already populated with decode information
      model.getPlate().getWells().forEach(w -> {
            wellGrid.setWellCellInventoryId(w.getLabel(), w.getInventoryId());
         });

      updateWellGrid();
      disableNextButton(wellGrid.getDecodedCellCount() <= 0);
   }

   /**
    * Used to specify the source of the image.
    *
    * @param <T> a subclass of {@link ImageSource}.
    *
    * @param imageSource the image source object.
    */
   public <T extends ImageSource> void setImageSource(T imageSource) {
      this.imageSource = imageSource;
      Image image = new Image(imageSource.getImageFileUrl());
      imageView.setImage(image);
      imageView.setCache(true);
   }

   @Override
   public double getImageWidth() {
      Image image = imageView.getImage();
      if (image == null) {
         throw new IllegalArgumentException("image is null");
      }
      return image.getWidth();
   }

   @Override
   public double getImageHeight() {
      Image image = imageView.getImage();
      if (image == null) {
         throw new IllegalArgumentException("image is null");
      }
      return image.getHeight();
   }

   /**
    * Creates a new well grid with the dimensions of the previous one.
    */
   private void createWellGrid() {
      Rectangle r;

      r = PlateDecoderPreferences.getInstance().getWellRectangle(model.getPlateType());
      Image image = imageView.getImage();
      double scale = (image == null) ? 1.0
         : imageView.getLayoutBounds().getWidth() / image.getWidth();

      wellGrid = new WellGrid(this, r.getX(), r.getY(), r.getWidth(), r.getHeight(), scale);
      wellGrid.setScale(scale);

      imageGroup.getChildren().clear();
      imageGroup.getChildren().add(imageView);
      imageGroup.getChildren().addAll(wellGrid.getWidgets());

      updateDecodedWellCount(wellGrid.getDecodedCellCount());
      wellGrid.update();
   }

   @Override
   protected Region createContents() {
      Node controls = createControlsPane();
      Node imagePane = createImagePane();

      BorderPane borderPane = new BorderPane();
      borderPane.setPadding(new Insets(5, 5, 5, 5));
      borderPane.setLeft(controls);
      borderPane.setCenter(imagePane);

      BorderPane.setMargin(controls, new Insets(5));

      return borderPane;
   }

   @Override
   protected void nextButtonAction() {
      if (wellGrid.getDecodedCellCount() <= 0) {
         throw new IllegalStateException("missing decoded wells");
      }

      // copy data to model
      Plate plate = model.getPlate();
      wellGrid.getDecodedCells().forEach(
         w -> plate.setWellInventoryId(w.getLabel(), w.getInventoryId()));

      // save current dimensions of plate to preferences so they are used next time
      PlateDecoderPreferences.getInstance().setWellRectangle(model.getPlateType(), wellGrid);
      super.nextButtonAction();
   }

   private Node createControlsPane() {
      PlateTypeChooser plateTypeChooser = new PlateTypeChooser();

      Button decodeButton = createButton("Decode", this::decodeImageAction);
      Button clearDecodeButton = createButton("Clear", this::clearDecodeAction);

      GridPane grid = new GridPane();
      grid.setVgap(5);
      grid.setHgap(5);
      grid.add(plateTypeChooser, 0, 0, 2, 1);
      grid.add(new PlateOrientationChooser(model.getPlateOrientationProperty()), 0, 1, 2, 1);
      grid.add(new BarcodePositionChooser(model.getBarcodePositionProperty()), 0, 2, 2, 1);
      grid.add(clearDecodeButton, 0, 3);
      grid.add(decodeButton, 1, 3);

      ColumnConstraints col1 = new ColumnConstraints();
      col1.setPercentWidth(50);
      ColumnConstraints col2 = new ColumnConstraints();
      col2.setPercentWidth(50);
      grid.getColumnConstraints().add(col1);
      grid.getColumnConstraints().add(col2);

      return grid;
   }

   private Pane createImagePane() {
      imageView = new ImageView();
      imageView.setPreserveRatio(true);
      imageView.setSmooth(true);

      imageGroup = new Group();
      imageGroup.getChildren().add(imageView);
      imagePane = new ScrollPane(imageGroup);

      filenameLabel = new Label("Filename:");

      GridPane grid = new GridPane();
      grid.add(imagePane, 0, 0);
      grid.add(filenameLabel, 0, 1);

      // subtract a few pixels so that scroll bars are not displayed
      imageView.fitWidthProperty().bind(grid.widthProperty().subtract(5));
      imageView.fitHeightProperty().bind(
         grid.heightProperty().subtract(filenameLabel.heightProperty()).subtract(5));

      imageView.fitWidthProperty().addListener((observable, oldValue, newValue) -> {
            // the actual dimensions are in imageView.getLayoutBounds().getWidth()
            Image image = imageView.getImage();
            if (image != null) {
               double newScale = imageView.getLayoutBounds().getWidth() / image.getWidth();
               wellGrid.setScale(newScale);
            }
         });

      imageView.fitHeightProperty().addListener((observable, oldValue, newValue) -> {
            // the actual dimensions are in imageView.getLayoutBounds().getHeight()
            Image image = imageView.getImage();
            if (image != null) {
               double newScale = imageView.getLayoutBounds().getHeight() / image.getHeight();
               wellGrid.setScale(newScale);
            }
         });

      return grid;
   }

   private String getFilenameFromImageSource() {
      try {
         URL imageUrl = new URI(imageSource.getImageFileUrl()).toURL();
         File file = new File(imageUrl.toURI());
         return file.toString();
      } catch (URISyntaxException | MalformedURLException ex) {
         throw new IllegalStateException("could not convert iamge URL to a filename");
      }
   }

   private void clearDecodeAction(@SuppressWarnings("unused") ActionEvent e) {
      wellGrid.clearWellCellInventoryId();
      updateWellGrid();
      updateDecodedWellCount(0);
      disableNextButton(true);
   }

   private void decodeImageAction(@SuppressWarnings("unused") ActionEvent e) {
      DecodeOptions decodeOptions = new DecodeOptions(model.getMinEdgeFactor(),
                                                      model.getMaxEdgeFactor(),
                                                      model.getScanGapFactor(),
                                                      model.getEdgeThreshold(),
                                                      model.getSquareDeviation(),
                                                      model.getDecoderCorrections(),
                                                      DecodeOptions.DEFAULT_SHRINK);
      DecodeImageTask worker = new DecodeImageTask(wellGrid,
                                                   model.getFlatbedDpi().getValue(),
                                                   model.getPlateOrientation(),
                                                   model.getPlateType(),
                                                   model.getBarcodePosition(),
                                                   model.getFlatbedBrightness(),
                                                   model.getFlatbedContrast(),
                                                   model.getDecoderDebugLevel(),
                                                   decodeOptions,
                                                   getFilenameFromImageSource());
      ProgressDialog dlg = new ProgressDialog(worker);
      dlg.setTitle("Decoding image");
      dlg.setHeaderText("Decoding image");

      worker.setOnSucceeded(event -> {
            DecodeResult result = (DecodeResult) worker.getValue();

            if (result.getResultCode() == ScanLibResult.Result.SUCCESS) {
               Set<DecodedWell> decodedWells = result.getDecodedWells();

               if (wellGrid.getDecodedCellCount() > 0) {
                  Set<DecodedWell> prevDecodedWells = wellGrid.getDecodedCells().stream()
                     .map(c -> new DecodedWell(c.getLabel(), c.getInventoryId()))
                     .collect(Collectors.toSet());
                  Set<DecodedWell> currentDecodedWells = decodedWells;

                  // compare this new result with the previous one
                  if (DecodeResult.compareDecodeResults(prevDecodedWells, currentDecodedWells)) {
                     // aggregate this new result
                     setDecodedCells(currentDecodedWells);
                  } else if (decodeMismatchErrorDialog()) {
                     wellGrid.clearWellCellInventoryId();
                     setDecodedCells(currentDecodedWells);
                  }
               } else {
                  setDecodedCells(decodedWells);
               }

               updateDecodedWellCount(wellGrid.getDecodedCellCount());
               updateWellGrid();
               disableNextButton(false);
            } else {
               LOG.error("decode failed: {}", result.getResultCode());
            }
         });

      worker.setOnFailed(event -> {
            LOG.error("The task failed: {}", event);
         });

      Thread th = new Thread(worker);
      th.setDaemon(true);
      th.start();
   }

   private void setDecodedCells(Set<DecodedWell> decodedWells) {
      for (DecodedWell well : decodedWells) {
         wellGrid.setWellCellInventoryId(well.getLabel(), well.getMessage());
      }
   }

   private void updateWellGrid() {
      wellGrid.update();
      updateDecodedWellCount(wellGrid.getDecodedCellCount());
   }

   private void updateDecodedWellCount(int count) {
      StringBuffer buf = new StringBuffer();
      buf.append(imageSource);
      if (count > 0) {
         buf.append(", tubes decoded: ");
         buf.append(count);
      }
      filenameLabel.setText(buf.toString());
   }

   /**
    *
    * See http://code.makery.ch/blog/javafx-dialogs-official/ for dialog examples.
    *
    * @return TRUE if these results should be used instead. FALSE if they should be discarded.
    */
   private boolean decodeMismatchErrorDialog() {
      // display error message to user
      ButtonType buttonTypeUseResult = new ButtonType("Use this result instead");
      ButtonType buttonTypeDiscardResult = new ButtonType("Discard this result");

      Alert dlg = JavaFxHelper.createDialog(
         AlertType.CONFIRMATION,
         "Decode Mismatch",
         "The results from this decode do not match the previous results.",
         "What would you like to do?");

      dlg.getButtonTypes().setAll(buttonTypeUseResult, buttonTypeDiscardResult);

      Optional<ButtonType> result = dlg.showAndWait();
      return (result.get() == buttonTypeUseResult);
   }

   @Override
   public void cellMoved(WellCell cell, double deltaX, double deltaY) {
      if (wellGrid == null) {
         throw new IllegalStateException("well grid is null");
      }
      wellGrid.cellMoved(cell, deltaX, deltaY);
   }

   /**
    * Called when the user double clicks on a cell in the grid.
    *
    * @param cell The cell in the grid that was double clicked.
    */
   @Override
   public void manualDecode(WellCell cell) {
      if ((wellGrid.getDecodedCellCount() <= 0)
          || wellGrid.containsDecodedLabel(cell.getLabel())) {
         // exit if this cell already has an inventory ID, or if it was never manually entered
         return;
      }

      String label = cell.getLabel();
      getManualDecodeInventoryId(label, cell.getInventoryId()).ifPresent(inventoryId -> {
            wellGrid.setWellCellInventoryId(cell.getLabel(), inventoryId, true);
            wellGrid.update();
            updateDecodedWellCount(wellGrid.getDecodedCellCount());
         });
   }

   private Optional<String> getManualDecodeInventoryId(String label, String inventoryId) {
      Set<String> deniedInventoryIds = wellGrid.getDecodedCells().stream()
         .filter(well -> !well.getInventoryId().equals(inventoryId))
         .map(well -> well.getInventoryId()).collect(Collectors.toSet());
      ManualDecodeDialog dlg = new ManualDecodeDialog(label, inventoryId, deniedInventoryIds);
      return dlg.showAndWait();
   }
}
