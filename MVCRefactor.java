package org.example;
import java.lang.reflect.Type;
import java.sql.SQLException;
import java.util.*;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.Scanner;

import java.sql.*;

public class MVCRefactor {
    public static void main(String[] args) {
        BoardRepository boardRepository = new SQLiteBoardRepository();
        SimulationRunRepository runRepository = new SQLiteRunRepository();
        ViewController controller = new ViewController(boardRepository, runRepository,
                new SimulationEngine(), new RunView(), new QueryView(), new ReportView());
        Scanner scanner = new Scanner(System.in);
        boolean running = true;
        while (running) {
            System.out.println("=== PCB SIMULATION MVC APPLICATION ===");
            System.out.println("1. Run a new simulation");
            System.out.println("2. Query stored simulation runs.");
            System.out.println("3. Exit");
            System.out.println("Select an option");
            String selection = scanner.nextLine();
            try {
                switch (selection) {
                    case "1" -> controller.createSimulationRun();
                    case "2" -> controller.querySimulationRun();
                    case "3" -> running = false;
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
        System.out.println("Application closed.");
    }
}
//Model-View-Controller pattern
// Controllers : includes View Controller and Simulation Engine.
// View Controller: connect the views to the model, repositories and simulation engine.
class ViewController {
    private final BoardRepository boardRepository;
    private final SimulationRunRepository runRepository;
    private final SimulationEngine simulationEngine;
    private final RunView runView;
    private final QueryView queryView;
    private final ReportView reportView;
    public ViewController(BoardRepository boardRepository,SimulationRunRepository runRepositor, SimulationEngine simulationEngine,
                          RunView runView, QueryView queryView , ReportView reportView) {
        this.boardRepository = boardRepository;
        this.runRepository = runRepositor;
        this.simulationEngine = simulationEngine;
        this.runView = runView;
        this.queryView = queryView;
        this.reportView = reportView;
    }
    public void createSimulationRun() {
        List<BoardProfile> boards = boardRepository.findAll();
        int boardId = runView.selectBoardId(boards);
        int quantity = runView.getQuantity();
        BoardProfile selectedBoard = boardRepository.findById(boardId).orElseThrow(()
                -> new IllegalArgumentException("Board type was not found"));
        SimulationRun result = simulationEngine.run(selectedBoard, quantity);
        long runId = runRepository.save(result);
        SimulationRun savedRun = runRepository.findById(runId).orElseThrow(() ->
                new IllegalArgumentException("Saved run could not be retrieved."));
        reportView.display(savedRun);
    }
    public void querySimulationRun() {
        List<SimulationRun> runs = runRepository.findAll();
        long selectedRunId = queryView.selectRun(runs);
        if (selectedRunId < 0) {
            return;
        }
        SimulationRun selectedRun = runRepository.findById(selectedRunId).orElseThrow(() ->
                new IllegalArgumentException("Run ID was not found."));
        reportView.display(selectedRun);
    }
}
// Simulation Engine class: simulate a production run and places the results into Simulation Run
class SimulationEngine {
    public SimulationRun run(BoardProfile board, int nboards) {
        if (nboards <=0) {
            throw new IllegalArgumentException("Number of boards must be greater than zero.");
        }
        List<Station> stations = createStations();
        Random rand = new Random();
        int totalFailure = 0;
        for (int i=1; i<=nboards; i++) {
            for(Station s: stations){
                FailureType result = s.process(rand,board);
                if (result==FailureType.STATION_FAILURE) {
                    s.nStationFailure += 1;
                    totalFailure +=1;
                    break;
                } else if (result == FailureType.PCB_DEFECT) {
                    s.nDefect += 1;
                    totalFailure += 1;
                    break;
                }
            }
        }
        Map<String, Integer> stationFailures = new LinkedHashMap<>();
        Map<String, Integer> defectFailures = new LinkedHashMap<>();
        for (Station s: stations) {
            stationFailures.put(s.getName(), s.getStationFailure());
            if (s.isDefectCheckingStation()) {
                defectFailures.put(s.getName(), s.getDefectFailure());
            }
        }
        int totalPassed = nboards-totalFailure;
        return new SimulationRun(0, null, board.getName(), nboards, stationFailures,
                defectFailures, totalFailure, totalPassed);
    }
    private List<Station> createStations() {
        return List.of(new ApplySolderPaste(), new PlaceComponents(), new ReflowSolder(),
        new OpticalInspection(), new HandSoldering(), new Cleaning(), new Depanelization(), new TestStation());
    }
}
// Models
// Simulation Model Storage: contain three records for test, sensor, gateway boards with
// the records containing their four failure percentages
// Board Profile Repository interface
interface BoardRepository {
    List<BoardProfile> findAll();
    Optional<BoardProfile> findById(int id);
}
// Use SQLite to store board profile
class SQLiteBoardRepository implements BoardRepository {
    private static final String DATABASE_URL = "jdbc:sqlite:pcb_simulation.db";
    public SQLiteBoardRepository() {
        createTable();
        insertDefaultBoards();
    }
    private Connection connect() throws SQLException {
        return DriverManager.getConnection(DATABASE_URL);
    }
    public void createTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS board_profile ( board_id INTEGER PRIMARY KEY,
                board_name TEXT NOT NULL UNIQUE, place_components_rate REAL NOT NULL,
                optical_inspection_rate REAL NOT NULL, hand_soldering_rate REAL NOT NULL,
                test_rate REAL NOT NULL);
                """;
        try (
                Connection connection = connect();
                Statement statement = connection.createStatement()
                ) {
            statement.executeUpdate(sql);
        } catch (SQLException e){
            throw new RuntimeException("Could not create board_profile table.", e);
        }
    }
    private void insertDefaultBoards() {
        String sql = """
                INSERT OR IGNORE INTO board_profile(board_id, board_name,
                place_components_rate, optical_inspection_rate, hand_soldering_rate, test_rate)
                VALUES (?, ?, ?, ?, ?, ?);
                """;
        try(
                Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql);
                ) {
            insertBoard(statement, 1, "Test Board", 0.05, 0.10, 0.05, 0.10);
            insertBoard(statement, 2, "Sensor Board", 0.002, 0.002, 0.004, 0.004);
            insertBoard(statement, 3, "Gateway Board", 0.004, 0.004, 0.008, 0.008);
        } catch (SQLException e){
            throw new RuntimeException("Could not insert default board files", e);
        }
    }
    private void insertBoard(PreparedStatement statement, int id, String name, double placeRate,
                             double opticalRate, double handSolderRate, double testRate)
                    throws  SQLException {
        statement.setInt(1, id);
        statement.setString(2, name);
        statement.setDouble(3, placeRate);
        statement.setDouble(4, opticalRate);
        statement.setDouble(5, handSolderRate);
        statement.setDouble(6, testRate);
        statement.executeUpdate();
    }
    @Override
    public List<BoardProfile> findAll() {
        List<BoardProfile> boards = new ArrayList<>();
        String sql = """
                SELECT * FROM board_profile ORDER BY board_id;
                """;
        try (
                Connection connection = connect();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql);
                ) {
            while(resultSet.next()) {
                boards.add(mapBoard(resultSet));
            }
            return boards;
        }catch (SQLException e) {
            throw new RuntimeException("Could not retrieve board profiles.", e);
        }
    }
    @Override
    public Optional<BoardProfile> findById(int id) {
        String sql = """
                SELECT * FROM board_profile WHERE board_id = ?;
                """;
        try (
                Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql);
                ) {
            statement.setInt(1,id);
            try(ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapBoard(resultSet));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Could not retrieve board profile.", e);
        }
    }
    private BoardProfile mapBoard(ResultSet resultSet) throws SQLException {
        return new BoardProfile(resultSet.getInt("board_id"), resultSet.getString("board_name"),
                resultSet.getDouble("place_components_rate"), resultSet.getDouble("optical_inspection_rate"),
                resultSet.getDouble("hand_soldering_rate"), resultSet.getDouble("test_rate"));
    }
}
// Simulation run storage: storage for any run of teh simulation engine
// Simulation run interface
interface SimulationRunRepository {
    long save(SimulationRun run);
    List<SimulationRun> findAll();
    Optional<SimulationRun> findById(long runId);
}
// Use SQLite to store simulation run
class SQLiteRunRepository implements SimulationRunRepository {
    private static final String DATABASE_URL = "jdbc:sqlite:pcb_simulation.db";
    private final Gson gson = new Gson();
    public SQLiteRunRepository() {
        createTable();
    }
    private Connection connect() throws SQLException {
        return DriverManager.getConnection(DATABASE_URL);
    }
    private void createTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS simulation_run ( run_id INTEGER PRIMARY KEY AUTOINCREMENT,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, pcb_type TEXT NOT NULL,
                pcb_run INTEGER NOT NULL, station_failures_json TEXT NOT NULL,
                defect_failures_json TEXT NOT NULL, total_failed INTEGER NOT NULL,
                total_passed INTEGER NOT NULL);
                """;
        try(
                Connection connection = connect();
                Statement statement = connection.createStatement();
        ) {
            statement.executeUpdate(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Could not create simulation_run table.",e);
        }
    }
    @Override
    public long save(SimulationRun run) {
        String sql = """
                INSERT INTO simulation_run(pcb_type, pcb_run, station_failures_json,
                 defect_failures_json, total_failed, total_passed) VALUES (?, ?, ?, ?, ?, ?);
                """;
        try(
                Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);
                ) {
            statement.setString(1, run.pcbType);
            statement.setInt(2, run.pcbRun);
            // Convert the two maps into JSON strings
            statement.setString(3, gson.toJson(run.stationFailures));
            statement.setString(4, gson.toJson(run.defectFailures));
            statement.setInt(5, run.totalFailed);
            statement.setInt(6, run.totalPassed);
            statement.executeUpdate();
            try (
                    ResultSet generatedKeys = statement.getGeneratedKeys()
                    ) {
                if (generatedKeys.next()) {
                    return generatedKeys.getLong(1);
                }
            }
            throw new SQLException("The database did not return a run ID.");
        } catch (SQLException e) {
            throw new RuntimeException("Could not save simulation run.", e);
        }
    }
    @Override
    public Optional<SimulationRun> findById(long runId) {
        String sql = """
                SELECT * FROM simulation_run WHERE run_id = ?;
                """;
        try(
                Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(sql)
                ) {
            statement.setLong(1, runId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if(resultSet.next()) {
                    return Optional.of(mapSimulationRun(resultSet));
                }
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Could not retrieve simulation run.", e);
        }
    }
    public List<SimulationRun> findAll() {
        List<SimulationRun> runs = new ArrayList<>();
        String sql = """
                SELECT * FROM simulation_run ORDER BY run_id DESC;
                """;
        try(
                Connection connection = connect();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery(sql);
                ) {
            while (resultSet.next()) {
                runs.add(mapSimulationRun(resultSet));
            }
            return runs;
        } catch (SQLException e) {
            throw new RuntimeException("Could not retrieve simulation run.", e);
        }
    }
    private SimulationRun mapSimulationRun(ResultSet resultSet)  throws SQLException {
        Type mapType = new TypeToken<Map<String, Integer>>(){}.getType();
        Map<String, Integer> stationFailures = gson.fromJson(resultSet.getString("station_failures_json"), mapType);
        Map<String, Integer> defectFailures = gson.fromJson(resultSet.getString("defect_failures_json"), mapType);
        return new SimulationRun(resultSet.getLong("run_id"), resultSet.getString("created_at"), resultSet.getString("pcb_type"),
                resultSet.getInt("pcb_run"), stationFailures, defectFailures,
                resultSet.getInt("total_failed"), resultSet.getInt("total_passed"));
    }
}
// Three Views : Run View, Query View and Report View
// Simulation Run UI:allow users to select a board type, select a number of boards, and initiate the run
class RunView {
    private final Scanner scanner = new Scanner(System.in);
    public int selectBoardId(List<BoardProfile> boards) {
        System.out.println("=== SIMULATION RUN UI ===");
        for(BoardProfile board: boards) {
            System.out.printf("%d. %s%n", board.getId(), board.getName());
        }
        System.out.print("Select board type: ");
        return Integer.parseInt(scanner.nextLine());
    }
    public int getQuantity() {
        System.out.print("Enter number of PCBs: ");
        return Integer.parseInt(scanner.nextLine());
    }
}
// Simulation Query UI: allow users to search the database of stored runs for display
class QueryView {
    private final Scanner scanner = new Scanner(System.in);
    public long selectRun(List<SimulationRun> runs) {
        System.out.println();
        System.out.println("=== SIMULATION QUERY UI ===");
        if (runs.isEmpty()) {
            System.out.println("No stored simulation runs.");
            return -1;
        }
        System.out.printf("%-10s %-20s %-12s %-12s%n", "Run ID", "Board Type", "PCBs Run", "Produced");
        for (SimulationRun run: runs) {
            System.out.printf("%-10s %-20s %-12s %-12s%n", run.runId, run.pcbType, run.pcbRun, run.totalPassed);
        }
        System.out.print("Enter a run ID to view: ");
        return Long.parseLong(scanner.nextLine());
    }
}
// Simulation Report UI: display a selected run
class ReportView {
    public void display(SimulationRun report) {
        System.out.println();
        System.out.println("=== SIMULATION REPORT UI ===");
        System.out.printf("%-35s %s%n", "Run ID:", report.runId);
        System.out.printf("%-35s %s%n", "PCB type:", report.pcbType);
        System.out.printf("%-35s %s%n", "PCBs run:", report.pcbRun);
        System.out.println("Station Failures");
        for (Map.Entry<String, Integer> station : report.stationFailures.entrySet()) {
            System.out.printf("%-35s %s%n", station.getKey(), station.getValue());
        }
        System.out.println("PCB Defect Failures");
        for (Map.Entry<String, Integer> defectStation : report.defectFailures.entrySet()) {
            System.out.printf("%-35s %s%n", defectStation.getKey(), defectStation.getValue());
        }
        System.out.println("Final Results");
        System.out.printf("%-35s %s%n", "Total failed PCBs:", report.totalFailed);
        System.out.printf("%-35s %s%n", "Total PCBs produced:", report.totalPassed);
    }
}
enum FailureType {
    NONE,
    STATION_FAILURE,
    PCB_DEFECT
}
class SimulationRun{
    long runId;
    String createdAt;
    String pcbType;
    int pcbRun;
    Map<String, Integer> stationFailures;
    Map<String, Integer> defectFailures;
    int totalFailed;
    int totalPassed;
    public SimulationRun(long runId, String createdAt, String pcbType, int pcbRun, Map<String, Integer> stationFailure,
                         Map<String, Integer> defectFailure, int totalFailed, int totalPassed) {
        this.runId =runId;
        this.createdAt = createdAt;
        this.pcbType= pcbType;
        this.pcbRun =pcbRun;
        this.stationFailures = stationFailure;
        this.defectFailures = defectFailure;
        this.totalFailed = totalFailed;
        this.totalPassed = totalPassed;
    }
}
// Template Method pattern is used in class Station.
// Station class contains the common processing algorithm
abstract class Station {
    String name;
    int nStationFailure=0;
    int nDefect=0;
    public Station(String name) {
        this.name = name;
    }
    public String getName() {
        return name;
    }
    public int getStationFailure() {
        return nStationFailure;
    }
    public int getDefectFailure() {
        return nDefect;
    }
    public boolean isDefectCheckingStation() {
        return false;
    }
    abstract double getDefectFailureRate(BoardProfile board);
    protected static final double StationFailureChance = 0.002;
    // Template Method Pattern is used here
    public FailureType process(Random random, BoardProfile board) {
        if (random.nextDouble() < StationFailureChance) {
            return FailureType.STATION_FAILURE;
        }
        // Subclasses will provide station-specific behavior
        double defectRate = getDefectFailureRate(board);
        if (random.nextDouble() < defectRate) {
            return FailureType.PCB_DEFECT;
        }
        return FailureType.NONE;
    }
}

class ApplySolderPaste extends Station {
    public ApplySolderPaste() {
        super( "Apply Solder Paste");
    }
    @Override
    public double getDefectFailureRate(BoardProfile board) {
        return 0.0;
    }
}
class ReflowSolder extends Station {
    public ReflowSolder() {
        super( "Reflow Solder");
    }
    @Override
    public double getDefectFailureRate(BoardProfile board) {
        return 0.0;
    }
}
class Depanelization extends Station {
    public Depanelization() {
        super("Depanelization");
    }
    @Override
    public double getDefectFailureRate(BoardProfile board) {
        return 0.0;
    }
}
class Cleaning extends Station {
    public Cleaning() {
        super("Cleaning");
    }
    @Override
    public double getDefectFailureRate(BoardProfile board) {
        return 0.0;
    }
}

class PlaceComponents extends Station{
    public PlaceComponents () {
        super( "Place Components");
    }
    @Override
    public double getDefectFailureRate(BoardProfile board) {
        return board.getPlaceComponentsRate();
    }
    @Override
    public boolean isDefectCheckingStation() {
        return true;
    }
}

class HandSoldering extends Station {
    public HandSoldering () {
        super("Hand Soldering/Assembly");
    }
    @Override
    public double getDefectFailureRate(BoardProfile board) {
        return board.getHandSolderingRate();
    }
    @Override
    public boolean isDefectCheckingStation() {
        return true;
    }
}
class TestStation extends Station {
    public TestStation () {
        super("Test (ICT or Flying Probe)");
    }
    @Override
    public double getDefectFailureRate(BoardProfile board) {
        return board.getTestRate();
    }
    @Override
    public boolean isDefectCheckingStation() {
        return true;
    }
}
class OpticalInspection extends Station {
    public OpticalInspection(){
        super("Optical Inspection");
    }
    @Override
    public double getDefectFailureRate(BoardProfile board) {
        return board.getOpticalInspectionRate();
    }
    @Override
    public boolean isDefectCheckingStation() {
        return true;
    }
}

class BoardProfile {
    private final int id;
    private final String name;
    private final double placeComponentsRate;
    private final double opticalInspectionRate;
    private final double handSolderingRate;
    private final double testRate;
    public BoardProfile(int id, String name, double placeComponentsRate,
                        double opticalInspectionRate, double handSolderingRate, double testRate) {
        this.id = id;
        this.name = name;
        this.placeComponentsRate = placeComponentsRate;
        this.opticalInspectionRate = opticalInspectionRate;
        this.handSolderingRate = handSolderingRate;
        this.testRate = testRate;
    }
    public int getId() {
        return id;
    }
    public String getName() {
        return name;
    }
    public double getPlaceComponentsRate() {
        return placeComponentsRate;
    }
    public double getOpticalInspectionRate() {
        return opticalInspectionRate;
    }
    public double getHandSolderingRate() {
        return handSolderingRate;
    }
    public double getTestRate() {
        return testRate;
    }
}
