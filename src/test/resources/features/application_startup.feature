Feature: Application startup

  Scenario: HelloFX starts successfully and shows its window
    When the HelloFX application is launched
    Then the application window becomes visible within 15 seconds
    And the window title is "Supernaut.FX: Hello"
    And the window shows a greeting containing "Hello, JavaFX"
