package com.xiphis.examples.parallelfor.seismic;

import com.xiphis.concurrent.IntRangeConcept;
import com.xiphis.concurrent.Parallel;

import java.awt.*;
import java.awt.image.MemoryImageSource;

/**
 * Created with IntelliJ IDEA.
 * User: acurtis
 * Date: 12/14/12
 * Time: 3:50 PM
 * To change this template use File | Settings | File Templates.
 */
public class Universe
{
  public static final int UNIVERSE_WIDTH = 1024;
  public static final int UNIVERSE_HEIGHT = 512;

  private static final int MAX_WIDTH = UNIVERSE_WIDTH + 1;
  private static final int MAX_HEIGHT = UNIVERSE_HEIGHT + 3;

  // Horizontal stress
  private final float[][] S = new float[MAX_HEIGHT][MAX_WIDTH];

  // Velocity at each grid point
  private final float[][] V = new float[MAX_HEIGHT][MAX_WIDTH];

  // Vertical stress
  private final float[][] T = new float[MAX_HEIGHT][MAX_WIDTH];

  // Coefficient related to modulus
  private final float[][] M = new float[MAX_HEIGHT][MAX_WIDTH];

  // Damping coefficients
  private final float[][] D = new float[MAX_HEIGHT][MAX_WIDTH];

  // Coefficient related to lightness
  private final float[][] L = new float[MAX_HEIGHT][MAX_WIDTH];

  private static final int COLOR_MAP_SIZE = 1024;
  private final Color[][] ColorMap = new Color[4][COLOR_MAP_SIZE];

  enum MaterialType
  {
    WATER {
      byte value() { return 0; }
      float Mvalue() { return 0.125f; }
      float Lvalue() { return 0.125f; }
    },
    SANDSTONE {
      byte value() { return 1; }
      float Mvalue() { return 0.3f; }
      float Lvalue() { return 0.4f; }
    },
    SHALE {
      byte value() { return 2; }
      float Mvalue() { return 0.5f; }
      float Lvalue() { return 0.6f; }
    };
    abstract byte value();
    abstract float Mvalue();
    abstract float Lvalue();
  }

  private final byte[][] material = new byte[MAX_HEIGHT][MAX_WIDTH];

  private static final int DAMPER_SIZE = 32;
  private int _pulseTime;
  private int _pulseCounter;
  private int _pulseX;
  private int _pulseY;

  private int[] _bitmap = new int[UNIVERSE_WIDTH * UNIVERSE_HEIGHT];
  public final MemoryImageSource _image = new MemoryImageSource(UNIVERSE_WIDTH, UNIVERSE_HEIGHT, _bitmap, 0, UNIVERSE_WIDTH);

  private static final Color[] MATERIAL_COLOR = new Color[] {
      new Color(96, 0, 0), // WATER
      new Color(0, 48, 48), // SANDSTONE
      new Color(32, 32, 32) // SHALE
  };

  public Universe()
  {
    _pulseCounter = _pulseTime = 100;
    _pulseX = UNIVERSE_WIDTH / 3;
    _pulseY = UNIVERSE_HEIGHT / 4;

    _image.setAnimated(true);

    // Initialize V, S and T to slightly non-zero values, in order to avoid denormal waves
    for (int i = 0; i < UNIVERSE_HEIGHT; ++i)
      for (int j = 0; j < UNIVERSE_WIDTH; ++j)
        T[i][j] = S[i][j] = V[i][j] = 1.0e-6f;

    for (int i = 1; i < UNIVERSE_HEIGHT - 1; ++i)
    {
      for (int j = 1; j < UNIVERSE_WIDTH - 1; ++j)
      {
        float x = ((float) j - UNIVERSE_WIDTH / 2.0f) / (UNIVERSE_WIDTH / 2.0f);
        float t = (float) i / (float) UNIVERSE_HEIGHT;
        MaterialType m;
        D[i][j] = 1.0f;

        // Coefficient values are fictitious, and chosen to visually exaggerate
        // physical effects such as Rayleigh waves.  The fabs/exp line generates
        // a shale layer with a gentle upwards slope and an anticline.
        if (t < 0.3f)
        {
          m = MaterialType.WATER;
        }
        else if (Math.abs(t - 0.7f + 0.2f*Math.exp(-8.0f*x*x) + 0.025f * x) <= 0.1f)
        {
          m = MaterialType.SHALE;
        }
        else
        {
          m = MaterialType.SANDSTONE;
        }

        material[i][j] = m.value();
        M[i][j] = m.Mvalue();
        L[i][j] = m.Lvalue();
      }
    }

    float scale = 2.0f / COLOR_MAP_SIZE;
    for (int k = 0; k < 3; ++k)
    {
      for (int i = 0; i < COLOR_MAP_SIZE; ++i)
      {
        float[] c = MATERIAL_COLOR[k].getRGBComponents(new float[4]);
        float t = (i - COLOR_MAP_SIZE / 2) * scale;
        c[0] += (t > 0f ? t : 0) * (1.0f - c[0]);
        c[1] += (0.5f * Math.abs(t)) * (1.0f - c[1]);
        c[2] += (t < 0f ? -t : 0) * (1.0f - c[2]);
        ColorMap[k][i] = new Color(c[0], c[1], c[2]);
      }
    }

    float d = 1.0f;
    for (int k = DAMPER_SIZE-1; k>0; --k)
    {
      d *= 1.0f - 1.0f/(DAMPER_SIZE * DAMPER_SIZE);
      for (int j = 1; j<UNIVERSE_WIDTH-1; ++j)
      {
        D[k][j] *= d;
        D[UNIVERSE_HEIGHT-1-k][j] *= d;
      }
      for (int i = 1; i < UNIVERSE_HEIGHT-1; ++i)
      {
        D[i][k] *= d;
        D[i][UNIVERSE_WIDTH-1-k] *= d;
      }
    }
  }

  void updatePulse()
  {
    if (_pulseCounter > 0)
    {
      float t = (_pulseCounter-_pulseTime/2)*0.05f;
      V[_pulseY][_pulseX] += 64*Math.sqrt(M[_pulseY][_pulseX]) * Math.exp(-t*t);
      --_pulseCounter;
    }
  }

  void updateStress(Rectangle r)
  {
    for (int i = r.y; i < r.y + r.height; ++i)
    {
      int addr = UNIVERSE_WIDTH * i + r.x;
      for (int j = r.x; j < r.x + r.width; ++j)
      {
        S[i][j] += M[i][j] * (V[i][j+1] - V[i][j]);
        T[i][j] += M[i][j] * (V[i+1][j] - V[i][j]);

        int index = Math.min(Math.max(0,(int) (V[i][j]*(COLOR_MAP_SIZE/2)) + COLOR_MAP_SIZE/2),COLOR_MAP_SIZE-1);
        _bitmap[addr++] = ColorMap[material[i][j]][index].getRGB();
      }
    }
    _image.newPixels(r.x, r.y, r.width, r.height);
  }

  void serialUpdateStress()
  {
    updateStress(new Rectangle(0, 0, UNIVERSE_WIDTH-1, UNIVERSE_HEIGHT-1));
  }

  private final class UpdateStressBody implements Parallel.Body<IntRangeConcept.IntRange, UpdateStressBody>
  {
    @Override
    public void apply(IntRangeConcept.IntRange range)
    {
      //System.out.println("begin = " + range.begin() + "  size = " + range.size());
      updateStress(new Rectangle(0, range.begin(), UNIVERSE_WIDTH-1, range.size()));
    }

    @Override
    public UpdateStressBody clone()
    {
      return this;
    }
  }

  void parallelUpdateStress()
  {
    Parallel.parallelFor(new IntRangeConcept().newInstance(0, UNIVERSE_HEIGHT-1), new UpdateStressBody());
  }

  void updateVelocity(Rectangle r)
  {
    for (int i = r.y; i < r.y + r.height; ++i)
    {
      for (int j = r.x; j < r.x + r.width; ++j)
      {
        V[i][j] = D[i][j]*(V[i][j] + L[i][j]*(S[i][j] - S[i][j-1] + T[i][j] - T[i-1][j]));
      }
    }
  }

  void serialUpdateVelocity()
  {
    updateVelocity(new Rectangle(1, 1, UNIVERSE_WIDTH-2, UNIVERSE_HEIGHT-2));
  }

  static final class UpdateVelocityBody implements Parallel.Body<IntRangeConcept.IntRange, UpdateVelocityBody>
  {
    final Universe _u;

    UpdateVelocityBody(Universe u)
    {
      _u = u;
    }


    @Override
    public void apply(IntRangeConcept.IntRange range)
    {
      _u.updateVelocity(new Rectangle(1, range.begin(), UNIVERSE_WIDTH - 1, range.size()));
    }

    @Override
    public UpdateVelocityBody clone()
    {
      return this;
    }
  }


  void parallelUpdateVelocity()
  {
    Parallel.parallelFor(new IntRangeConcept().newInstance(1, UNIVERSE_HEIGHT - 2), new UpdateVelocityBody(this));
  }

  void serialUpdateUniverse()
  {
    updatePulse();
    serialUpdateStress();
    serialUpdateVelocity();
  }

  void parallelUpdateUniverse()
  {
    updatePulse();
    parallelUpdateStress();
    parallelUpdateVelocity();
  }

  boolean tryPutNewPulseSource(int x, int y)
  {
    if (_pulseCounter == 0)
    {
      _pulseCounter = _pulseTime;
      _pulseX = x;
      _pulseY = y;
      return true;
    }
    return false;
  }

}

