# PCB Assembly Simulation

A Java-based printed circuit board (PCB) manufacturing simulation developed as an Object-Oriented Analysis and Design capstone project.

The application simulates PCB movement through an eight-station assembly line, records board-defect and equipment-failure results, stores simulation runs in a database, and allows users to run, search, and display simulation reports through a text-based MVC interface.

## Project Overview

The simulated production line contains the following stations:

1. Apply Solder Paste
2. Place Components
3. Reflow Solder
4. Optical Inspection
5. Hand Soldering/Assembly
6. Cleaning
7. Depanelization
8. Test (ICT or Flying Probe)

A board may be discarded for either of these reasons:

- A PCB defect is detected at a defect-checking station.
- A manufacturing station experiences a failure.

The simulation supports three PCB types:

- Test Board
- Sensor Board
- Gateway Board

Each board type has different defect probabilities. Every manufacturing station also has a `0.2%` chance of equipment failure.

## Main Features
- Run a simulation for any supported PCB type.
- Select the number of PCBs to process.
- Simulate all eight manufacturing stations.
- Track PCB defect failures separately from station failures.
- Calculate the total number of failed and completed PCBs.
- Save board profiles and simulation results in a database.
- Search previously completed simulation runs.
- Display a formatted report for a selected run.
- Separate application responsibilities using the MVC architecture.

## MVC Architecture

The application is organized into three primary layers.

### Model

The model stores application data and business entities, including:

- PCB board profiles
- Failure probabilities
- Simulation run information

### View

The project contains three text-based views:

- **Run View** — selects a PCB type, enters a run quantity, and starts a simulation.
- **Query View** — searches and lists previously stored simulation runs.
- **Report View** — displays the complete results of a selected run.

### Controller

The controller coordinates communication between the views, simulation engine, and repositories. It receives user requests, validates input, starts simulations, retrieves stored data, and selects the appropriate output view.

## Technology Stack
- Java
- Maven
- SQLite
- JDBC
- IntelliJ IDEA
- Git and GitHub
- UML modeling tool

## Database Design

The application uses persistent storage for both PCB configuration data and simulation results.

### Board Profile Storage

Stores the permanent failure probabilities for:

- Test Board
- Sensor Board
- Gateway Board
- 
### Simulation Run Storage

Stores enough information to reproduce a complete simulation report.

## Example Report

```text
PCB type: Sensor Board
PCBs run: 1000

Station Failures
Apply Solder Paste: 2
Place Components: 3
Reflow Solder: 0
Optical Inspection: 1
Hand Soldering/Assembly: 2
Cleaning: 1
Depanelization: 3
Test (ICT or Flying Probe): 2

PCB Defect Failures
Place Components: 3
Optical Inspection: 2
Hand Soldering/Assembly: 5
Test (ICT or Flying Probe): 4

Final Results
Total failed PCBs: 28
Total PCBs produced: 972
```
## Learning Outcomes

This project demonstrates:

- Refactoring an existing application into MVC
- Applying object-oriented design principles
- Designing database-backed Java applications
- Separating persistence logic with repositories
- Using design patterns in a practical system
- Creating UML documentation
- Building interactive text-based user interfaces
