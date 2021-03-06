package application.controllers;
import application.Confidence;
import application.Main;
import application.PathCD;
import application.Play;
import application.bashwork.BashCommand;
import application.bashwork.ManageFolder;
import application.listeners.CreationListCell;
import application.values.PicPath;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javafx.stage.Popup;
import javafx.util.Callback;
import javafx.util.Duration;

/**
 * Controller for the view window of the application.
 */
public class ViewController {
    private String _choice;
    private MediaPlayer _player;
    private Boolean _muted = false;
    private ExecutorService team = Executors.newSingleThreadExecutor();
    private static final Image MUTE = new Image(PicPath.VIEW + "/mute.png");
    private static final Image UNMUTE = new Image(PicPath.VIEW + "/unmute.png");
    private static final Image PLAY = new Image(PicPath.VIEW + "/play.png");
    private static final Image PAUSE = new Image(PicPath.VIEW + "/pause.png");
    private static final Image RETRY = new Image(PicPath.VIEW + "/retry.png");
    private double _currentVidDura; //seconds

    @FXML private ListView stuffCreated;

    @FXML private MediaView view;
    @FXML private HBox playOptions;
    @FXML private Button playButton;
    @FXML private Button muteButton;
    @FXML private CheckBox favOption;
    @FXML private Slider confidence;
    @FXML private Slider playTimer;
    @FXML private Button favBtn;
    @FXML private HBox creationOptions;
    @FXML private ImageView muteImg;
    @FXML private ImageView playImg;

    /**
     * This method will add the existing creation to the ListView
     * It will also set the cell factory for stuffcreated listview.
     */
    public void initialize() throws Exception {//TODO concurrency for this??
        team.submit(() -> {
            try {
                setCreations("creations");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        creationOptions.setDisable(true);
        playButton.setDisable(true);
        playOptions.setDisable(true);
    }

    /**
     * The getTheSelection method will retrieve the user selection of the ListView.
     * @param mouseEvent
     */
    @FXML
    public void getTheSelection(javafx.scene.input.MouseEvent mouseEvent) throws Exception {

        try{
            ObservableList selectedCreation = stuffCreated.getSelectionModel().getSelectedItems();
            _choice = selectedCreation.get(0).toString();
            if (_choice != null){
                Main.getController().popupHelper("Try playing your creation!", false);
                playImg.setImage(PLAY);
                playButton.setDisable(false);
                creationOptions.setDisable(false);

                File file = new File(ManageFolder.findPath(_choice, true));
                Media media = new Media(file.toURI().toString());
                _player = new MediaPlayer(media); //Set up player to be played.
                _player.setOnReady(() -> {
                    _currentVidDura = _player.getTotalDuration().toSeconds();
                    playTimer.maxProperty().set(_currentVidDura);
                });
                sliderSetUp();

                //Get confidence rating from file.
                int rate = Integer.parseInt(ManageFolder.readFile(ManageFolder.findPath(_choice, false) + "/confidence.txt"));
                if (rate == 0){
                    confidence.setValue(1);
                } else {
                    confidence.setValue(rate); //Set up confidence for viewing.
                }

                //When the player ends...
                _player.setOnEndOfMedia(() -> {
                    try {
                        team.submit(new Play(_choice));
                        setColourImmediately();
                        Main.getController().popupHelper("How well do you know the word now? Rate yourself!", false);
                        resetPlayer();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });

                view.setDisable(false);
                view.setMediaPlayer(_player);
            }
        }catch (Exception e){
        }
    }

    /**
     * set up a slider for video playing
     * Inspired by: https://stackoverflow.com/questions/15475457/how-to-use-timeline-when-playing-videos-using-javafx
     */
    private void sliderSetUp(){
        _player.currentTimeProperty().addListener((observableValue, duration, t1) -> {
            Duration currentTime = _player.getCurrentTime();
            playTimer.setValue(currentTime.toSeconds());
        });

        playTimer.valueProperty().addListener(observable -> {
            if (playTimer.isValueChanging()){
                _player.seek(Duration.seconds(playTimer.getValue()));
                Duration currentTime = _player.getCurrentTime();
                playTimer.setValue(currentTime.toSeconds());
            }
        });
    }

    /**
     * The playVideo method will play the video when the button "Play" is clicked.
     *
     */
    @FXML
    public void playVideo() {
        if (_choice == null){
            return;
        }

        view.setVisible(true);
        stuffCreated.setDisable(true);
        playOptions.setDisable(false); //Show the video manipulation options.

        if (_player.getStatus().equals(MediaPlayer.Status.PLAYING)) {
            _player.pause();
            stuffCreated.setDisable(false);
            playImg.setImage(PLAY);
        } else {
            _player.play();
            playImg.setImage(PAUSE);
        }
    }

    /**
     * The videoPlay method will execute when one of the embedded video player buttons are pressed. Depending on the type of button pressed,
     * a different piece of code will execute.
     * @param event
     */
    @FXML
    public void videoPlay(ActionEvent event) throws Exception { //TODO if user presses something else
        String id = ((Control)event.getSource()).getId();

        if (id.equals("stopButton")){
            _player.stop();
            resetPlayer();
        } else if (id.equals("muteButton")){
            if (!_muted){
                _player.setMute(true);
                muteImg.setImage(UNMUTE);
                _muted = true;
            } else {
                _player.setMute(false);
                muteImg.setImage(MUTE);
                _muted = false;
            }
        }
    }

    /**
     * The deleteVideo method will execute to delete a video when requested by the user.
     * @param event
     * @throws IOException
     */
    @FXML
    public void deleteVideo(ActionEvent event) throws Exception {
        if(_choice!=null){
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Confirmation Delete");
            alert.setHeaderText("Check again!");
            alert.setContentText("Are you sure to delete this?");

            Optional<ButtonType> result = alert.showAndWait();
            if (result.get() == ButtonType.OK){
                resetPlayer();
                _player.dispose();

                team.submit(new Task<Void>() {
                    @Override
                    protected Void call() throws Exception {
                        String path = ManageFolder.findPath(_choice, true); //finds the relevant creation
                        String command = "rm -f \"" + path + "\"";
                        new BashCommand().bash(command);

                        Platform.runLater(() -> {
                            _choice = null;
                            playButton.setDisable(true);
                            creationOptions.setDisable(true);

                            try {
                                tickFav(); //get the list of creations for currently ticked option.
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });
                        return null;
                    }
                });
            } else if (result.get() == ButtonType.CANCEL){
                return;
            }
        } else {
        }
    }

    /**
     * record the changes of confidence given by user
     * @param mouseEvent
     * @throws Exception
     */
    @FXML
    public void changeConfidence(javafx.scene.input.MouseEvent mouseEvent) throws Exception {
        int rating = (int) confidence.getValue();
        Confidence change = new Confidence(_choice, rating);
        team.submit(change);

        change.setOnSucceeded(workerStateEvent -> {
            try {
                setColourImmediately();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

    }

    /**
     * move the creation to favourite folder when user click the favourite button
     *
     * @throws Exception
     */
    @FXML
    public void favourite() throws Exception { //TODO implement remove favourites, button changes when this option is ticked.
        if (_choice != null){

            String file = "\"" + ManageFolder.findPath(_choice, true) + "\"";
            String file2 = "\"" + PathCD.getPathInstance().getPath() + "/mydir/creations/favourites/" + _choice + ".mp4\"";

            resetPlayer();
            String command = "mv " + file + " " + file2;

            new BashCommand().bash(command);

        } else {
        }
        tickFav();
    }

    /**
     * Load the creations based on the favourites checkbox- if ticked, load in favourites only. If not ticked, load in all creations.
     * @throws Exception
     */
    @FXML
    public void tickFav() throws Exception { //TODO IMPLEMENT UNFAVOURITE
        if (favOption.isSelected()){
            setCreations("favourites");
        } else {
            setCreations("creations");
        }
    }

    /**
     * If user wants to clear data for plays and confidence rating, then pressing this button resets data saved in the text files.
     * @throws Exception
     */
    @FXML
    public void clearDataForCreation() throws Exception {
        if (_choice != null){
            ManageFolder.writeToFile(ManageFolder.findPath(_choice, false) + "/plays.txt", "0");
            ManageFolder.writeToFile(ManageFolder.findPath(_choice, false) + "/confidence.txt", "0");
            setColourImmediately();
        }
    }

    /**
     * The setCreations method add creations in the stuffCreated list view, and set the cell factory for stuffcreated listview
     * @param path
     * @throws Exception
     */

    private void setCreations(String path) throws Exception { //TODO USE THIS!
        ArrayList<String> creations = null;
        try {
            creations = ManageFolder.getCreations(path);
        } catch (Exception e) {
            e.printStackTrace();
        }
        stuffCreated.getItems().clear();
        stuffCreated.getItems().setAll(creations);
        stuffCreated.setCellFactory((Callback<ListView<String>, ListCell<String>>) param -> new CreationListCell());
    }


    /**
     * The resetPlay() method will reset the player to its initial state
     * @throws Exception
     */
    private void resetPlayer() throws Exception {
        playTimer.setValue(0);
        _player.seek(new Duration(0));

        playOptions.setDisable(true);
        muteImg.setImage(MUTE);
        playImg.setImage(RETRY);

        view.setDisable(true);
        _player.stop();

        stuffCreated.setDisable(false);
        tickFav();
    }

    /**
     * Method called everytime a video changes confidence or view count. Check whether item needs reviewing or not.
     * Code inspired from: https://stackoverflow.com/questions/20936101/get-listcell-via-listview
     */
    private void setColourImmediately() throws Exception {
        stuffCreated.getSelectionModel().select(_choice);
        int index = stuffCreated.getSelectionModel().getSelectedIndex();
        Object[]cells = stuffCreated.lookupAll(".cell").toArray();
        Cell cell = (Cell) cells[index];

        String confidence = ManageFolder.readFile(ManageFolder.findPath(_choice, false) + "/confidence.txt");
        String plays = ManageFolder.readFile(ManageFolder.findPath(_choice, false) + "/plays.txt");

        String style = "-fx-background-color:";

        if (Integer.parseInt(plays) == 0) { //If video has never been played.
            cell.setStyle("-fx-background-color: #93D4EE;");
        } else if (Integer.parseInt(confidence) < 3) { //If confidence is below 3
            cell.setStyle("-fx-background-color: orange;");
        } else {
            cell.setStyle("-fx-highlight-fill: derive(-fx-control-inner-background,-20%); -fx-highlight-text-fill: -fx-text-inner-color;");
        }

        cell.setStyle(style);
    }

}
