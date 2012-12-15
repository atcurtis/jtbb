package com.xiphis.examples.parallelfor.seismic;

import com.xiphis.concurrent.TaskScheduler;

import javax.swing.*;

/**
 * Created with IntelliJ IDEA.
 * User: acurtis
 * Date: 12/14/12
 * Time: 3:33 PM
 * To change this template use File | Settings | File Templates.
 */
public class Main
{
  public static void main(String[] args)
  {
    long mainStartTime = System.nanoTime();

    System.out.println("Constructing Universe");
    Universe u = new Universe();


    System.out.println("Opening Window");
    SeismicVideo video = new SeismicVideo(u, -1, TaskScheduler.automatic);

    video.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    video.pack();
    video.setVisible(true);
  }




}
