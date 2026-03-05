# EventManager – Event & Venue Management Application

## Overview
This project was developed as part of the PIDEV – 3rd Year Engineering Program at **Esprit School of Engineering** (Academic Year 2025–2026).

It is a JavaFX desktop application that allows users to manage events, venues, registrations, tickets, and user accounts through both a front-end user interface and a back-end admin dashboard.

## Features
- User authentication (Login / Signup)
- Event creation, listing, and detail view
- Venue (Lieu) management with geolocation (latitude/longitude)
- Event registration and ticket management
- Admin dashboard for users, events, and venues
- Interactive map integration via JavaFX WebView

## Tech Stack

### Frontend
- JavaFX 21 (FXML, Controls, WebView)

### Backend
- Java 17
- MySQL (via MySQL Connector 8.0.33)
- Maven (build & dependency management)

## Architecture
The project follows an MVC (Model-View-Controller) architecture:
- `models/` – Entity classes (Evenement, Lieu, User, Ticket, Inscription, EvaluationLieu)
- `controllers/` – Front and Back controllers organized by module
- `services/` – Business logic and database operations
- `gui/` – JavaFX application entry points

## Contributors
<!-- Add your team members here -->
- ...

## Academic Context
Developed at **Esprit School of Engineering – Tunisia**  
PIDEV – 3A | 2025–2026

## Getting Started

### Prerequisites
- Java 17+
- Maven 3.8+
- MySQL Server

### Installation
```bash
git clone https://github.com/<your-username>/Esprit-PIDEV-3A21-2026-EventManager.git
cd Esprit-PIDEV-3A21-2026-EventManager
mvn clean install
mvn javafx:run
```

Configure your MySQL connection before running.

## Acknowledgments
Esprit School of Engineering – Tunisia  
Academic Year 2025–2026
