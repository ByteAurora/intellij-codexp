package com.github.ilovegamecoding.intellijcodexp.form;

import com.github.ilovegamecoding.intellijcodexp.services.CodeXPService;
import com.intellij.openapi.application.ApplicationManager;

import javax.swing.*;

public class CodeXPDashboard {
    private CodeXPService codeXPService = ApplicationManager.getApplication().getService(CodeXPService.class);
    public JPanel pMain;
    public JProgressBar pbCurrentLevelProgress;
    public JLabel lblNextLabel;
    public JLabel lblCurrentLevel;
    public JLabel tvCurrentLevelXP;
    public JTextField nicknameTextField;
    public JPanel lblCompletedChallenges;
    public JLabel lblTotalXP;
    public JLabel lblLevel;
    public JButton resetButton;
    public JPanel pEventStatistics;
    public JPanel pCompletedChallenges;
    public JPanel pChallenges;

    public CodeXPDashboard() {
        resetButton.addActionListener(e -> {
            codeXPService.resetPlugin();
        });
    }
}