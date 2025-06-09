package pt.ipp.isep.dei.examples.basic.ui;

import pt.ipp.isep.dei.examples.basic.domain.Calculator;

public class Main {

    public static void main(String[] args) {
        Calculator calculator = new Calculator();
        System.out.println(calculator.sum(3, 4));
    }
}
