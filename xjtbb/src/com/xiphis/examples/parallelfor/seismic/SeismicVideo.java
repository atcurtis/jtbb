package com.xiphis.examples.parallelfor.seismic;

import com.xiphis.concurrent.TaskScheduler;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Created with IntelliJ IDEA.
 * User: acurtis
 * Date: 12/14/12
 * Time: 3:35 PM
 * To change this template use File | Settings | File Templates.
 */
public class SeismicVideo extends JFrame
{
  final Universe _u;
  int _numberOfFrames;
  int _threadsHigh;
  private boolean _initIsParallel;
  private boolean _updating = true;
  private boolean _running = true;
  private Canvas _canvas = new Canvas(getGraphicsConfiguration());

  public SeismicVideo(Universe u, int numberOfFrames, int threadsHigh)
  {
    this(u, numberOfFrames, threadsHigh, true);
  }

  public SeismicVideo(Universe u, int numberOfFrames, int threadsHigh, boolean initIsParallel)
  {
    super("SeismicVideo");
    _u = u;
    _numberOfFrames = numberOfFrames;
    _threadsHigh = threadsHigh;

    JPanel panel = new JPanel();
    panel.add(_canvas);

    _canvas.setSize(new Dimension(Universe.UNIVERSE_WIDTH, Universe.UNIVERSE_HEIGHT));
    _canvas.setMinimumSize(new Dimension(Universe.UNIVERSE_WIDTH, Universe.UNIVERSE_HEIGHT));

    _canvas.addKeyListener(new KeyAdapter()
    {
      @Override
      public void keyPressed(KeyEvent e)
      {
        switch (e.getKeyCode())
        {
        case KeyEvent.VK_SPACE:
          _initIsParallel = !_initIsParallel;
          break;
        case KeyEvent.VK_P:
          _initIsParallel = true;
          break;
        case KeyEvent.VK_S:
          _initIsParallel = false;
          break;
        case KeyEvent.VK_E:
          _updating = true;
          break;
        case KeyEvent.VK_D:
          _updating = false;
          break;
        case KeyEvent.VK_ESCAPE:
          _running = false;
          break;
        default:
          return;
        }
        System.out.println("Parallel = " + _initIsParallel);
      }
    });

    _canvas.addMouseListener(new MouseAdapter()
    {
      @Override
      public void mouseClicked(MouseEvent e)
      {
        _u.tryPutNewPulseSource(e.getX(), e.getY());
      }
    });

    getContentPane().add(panel);

    new Worker().execute();
  }

  public boolean nextFrame()
  {
    if (!_running)
      return false;
    Thread.yield();
    return true;
  }


  TaskScheduler scheduler;

  public void onProcess()
  {
    Image img = createImage(_u._image);
    scheduler = new TaskScheduler(_threadsHigh);
    do{
      if (_initIsParallel)
        _u.parallelUpdateUniverse();
      else
        _u.serialUpdateUniverse();
      if (_numberOfFrames > 0)
        --_numberOfFrames;
      _canvas.getGraphics().drawImage(img, 0, 0, Universe.UNIVERSE_WIDTH, Universe.UNIVERSE_HEIGHT, null);
    } while (nextFrame() && _numberOfFrames != 0);
  }

  private class Worker extends SwingWorker<Void, Image>
  {

    @Override
    protected Void doInBackground() throws Exception
    {
      System.out.println("bar");

      onProcess();

      System.out.println("foo");
      return null;
    }
  }


}
