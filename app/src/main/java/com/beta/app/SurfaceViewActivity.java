package com.beta.app;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class SurfaceViewActivity extends Activity {
    BallBounces ball;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ball = new BallBounces(this);
        setContentView(ball);
    }
}

class BallBounces extends SurfaceView implements SurfaceHolder.Callback {
    private static int NONE = 0;
    private static int DRAG = 1;
    private static int ZOOM = 2;
    private static int DRAG_BALL = 3;

    GameThread thread;
    int screenW; //Device's screen width.
    int screenH; //Device's screen height.
    int ballX; //Ball x position.
    int ballY; //Ball y position.
    int initialY;
    float dY; //Ball vertical speed.
    int ballW;
    int ballH;
    int bgrW;
    int bgrH;
    int bgrScroll;
    int dBgrY; //Background scroll speed.
    float acc;
    Bitmap ball, bgr, bgrReverse;
    boolean reverseBackgroundFirst;
    boolean ballFingerMove;

    //Measure frames per second.
    long now;
    int framesCount = 0;
    int framesCountAvg = 0;
    long framesTimer = 0;
    Paint fpsPaint = new Paint();

    //Frame speed
    long timeNow;
    long timePrevFrame = 0;
    long timeDelta;

    // zoom
    private ScaleGestureDetector detector;
    private static final float MIN_ZOOM = 1f;
    private static final float MAX_ZOOM = 5f;
    private float scaleFactor = 1.f;

    // mode of click
    private int mode;

    // was finger dragged across screen
    private boolean dragged = true;

    //These two variables keep track of the X and Y coordinate of the finger when it first
    //touches the screen
    private float startX = 0f;
    private float startY = 0f;

    //These two variables keep track of the amount we need to translate the canvas along the X
    //and the Y coordinate
    private float translateX = 0f;
    private float translateY = 0f;
    //These two variables keep track of the amount we translated the X and Y coordinates, the last time we
    //panned.
    private float previousTranslateX = 0f;
    private float previousTranslateY = 0f;

    public BallBounces(Context context) {
        super(context);
        ball = BitmapFactory.decodeResource(getResources(), R.drawable.football); //Load a ball image.
        bgr = BitmapFactory.decodeResource(getResources(), R.drawable.wall1); //Load a background.
        ballW = ball.getWidth();
        ballH = ball.getHeight();

        //Create a flag for the onDraw method to alternate background with its mirror image.
        reverseBackgroundFirst = false;

        //Initialise animation variables.
        acc = 0.2f; //Acceleration
        dY = 0; //vertical speed
        initialY = 100; //Initial vertical position
        bgrScroll = 0;  //Background scroll position
        dBgrY = 0; //Scrolling background speed

        fpsPaint.setTextSize(30);

        //Set thread
        getHolder().addCallback(this);

        setFocusable(true);
        detector = new ScaleGestureDetector(getContext(), new ScaleListener());
    }

    @Override
    public void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        //This event-method provides the real dimensions of this custom view.
        screenW = w;
        screenH = h;

        int ratio = w / bgr.getWidth();
        bgr = Bitmap.createScaledBitmap(bgr, w, bgr.getHeight() * ratio, true); //Scale background to fit the screen.
        bgrW = bgr.getWidth();
        bgrH = bgr.getHeight();

        //Create a mirror image of the background (horizontal flip) - for a more circular background.
        Matrix matrix = new Matrix();  //Like a frame or mould for an image.
        matrix.setScale(-1, 1); //Horizontal mirror effect.
        bgrReverse = Bitmap.createBitmap(bgr, 0, 0, bgrW, bgrH, matrix, true); //Create a new mirrored bitmap by applying the matrix.

        ballX = (int) (screenW / 2) - (ballW / 2); //Centre ball X into the centre of the screen.
        ballY = -50; //Centre ball height above the screen.
    }

    //***************************************
    //*************  TOUCH  *****************
    //***************************************
    @Override
    public synchronized boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                mode = DRAG;
                // Initial finger position taking into account previous moves
                startX = event.getX() - previousTranslateX;
                startY = event.getY() - previousTranslateY;
                break;

            case MotionEvent.ACTION_MOVE:
                // Mode is already set to DRAG
                // Finger movement
                translateX = event.getX() - startX;
                translateY = event.getY() - startY;

                //We cannot use startX and startY directly because we have adjusted their values using the previous translation values.
                //This is why we need to add those values to startX and startY so that we can get the actual coordinates of the finger.
                double distance = Math.sqrt(Math.pow(event.getX() - (startX + previousTranslateX), 2) +
                                Math.pow(event.getY() - (startY + previousTranslateY), 2)
                );

                if (distance > 0) {
                    dragged = true;
                }

                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                //The second finger has been placed on the screen and so we need to set the mode to ZOOM
                mode = ZOOM;
                break;

            case MotionEvent.ACTION_UP:
                //All fingers are off the screen and so we're neither dragging nor zooming.
                mode = NONE;

                dragged = false;
                // save finger movement
                previousTranslateX = translateX;
                previousTranslateY = translateY;
                break;

            case MotionEvent.ACTION_POINTER_UP:
                //The second finger is off the screen and so we're back to dragging.
                mode = DRAG;
                // second finger (not necessary?)
                previousTranslateX = translateX;
                previousTranslateY = translateY;
                break;
        }

        detector.onTouchEvent(event);
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                ballX = (int) event.getX() - ballW / 2;
                ballY = (int) event.getY() - ballH / 2;

                ballFingerMove = true;
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                ballX = (int) event.getX() - ballW / 2;
                ballY = (int) event.getY() - ballH / 2;

                break;
            }

            case MotionEvent.ACTION_UP:
                ballFingerMove = false;
                dY = 0;
                break;
        }

        //The only time we want to re-draw the canvas is if we are panning and the finger moved or if we're zooming
        if ((mode == DRAG && scaleFactor != 1f && dragged) || mode == ZOOM) {
            invalidate();
        }
        return true;
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.save();

        // zoom
        canvas.scale(scaleFactor, scaleFactor);

        // avoid panning past last bound
        if ((translateX * -1) < 0) {
            translateX = 0;
        }

        // avoid panning past right bound
        else if ((translateX * -1) > (scaleFactor - 1) * bgrW) {
            translateX = (1 - scaleFactor) * bgrW;
        }

        // avoid panning past top bound
        if (translateY * -1 < 0) {
            translateY = 0;
        }

        // avoid panning past bottom bound
        else if ((translateY * -1) > (scaleFactor - 1) * bgrH) {
            translateY = (1 - scaleFactor) * bgrH;
        }

        // perform panning of image taking into account zoom
        canvas.translate(translateX / scaleFactor, translateY / scaleFactor);


        //Draw scrolling background.
        Rect fromRect1 = new Rect(0, 0, bgrW - bgrScroll, bgrH);
        Rect toRect1 = new Rect(bgrScroll, 0, bgrW, bgrH);

        Rect fromRect2 = new Rect(bgrW - bgrScroll, 0, bgrW, bgrH);
        Rect toRect2 = new Rect(0, 0, bgrScroll, bgrH);

        if (!reverseBackgroundFirst) {
            canvas.drawBitmap(bgr, fromRect1, toRect1, null);
            canvas.drawBitmap(bgrReverse, fromRect2, toRect2, null);
        } else {
            canvas.drawBitmap(bgr, fromRect2, toRect2, null);
            canvas.drawBitmap(bgrReverse, fromRect1, toRect1, null);
        }

        //Next value for the background's position.
        if ((bgrScroll += dBgrY) >= bgrW) {
            bgrScroll = 0;
            reverseBackgroundFirst = !reverseBackgroundFirst;
        }

        //Compute roughly the ball's speed and location.
        if (!ballFingerMove) {
            ballY += (int) dY; //Increase or decrease vertical position.
            if (ballY > (screenH - ballH)) {
                dY = (-1) * dY; //Reverse speed when bottom hit.
            }
            dY += acc; //Increase or decrease speed.
        }

        //DRAW BALL
        float scaledBallX = ballX / scaleFactor;
        float scaledBallY = ballY / scaleFactor;

        canvas.drawBitmap(ball, scaledBallX, scaledBallY, null);
        canvas.restore();

        //Measure frame rate (unit: frames per second).
        now = System.currentTimeMillis();
        canvas.drawText(framesCountAvg + " fps", 40, 70, fpsPaint);
        framesCount++;
        if (now - framesTimer > 1000) {
            framesTimer = now;
            framesCountAvg = framesCount;
            framesCount = 0;
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        thread = new GameThread(getHolder(), this);
        thread.setRunning(true);
        thread.start();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        boolean retry = true;
        thread.setRunning(false);
        while (retry) {
            try {
                thread.join();
                retry = false;
            } catch (InterruptedException e) {

            }
        }
    }


    class GameThread extends Thread {
        private SurfaceHolder surfaceHolder;
        private BallBounces gameView;
        private boolean run = false;

        public GameThread(SurfaceHolder surfaceHolder, BallBounces gameView) {
            this.surfaceHolder = surfaceHolder;
            this.gameView = gameView;
        }

        public void setRunning(boolean run) {
            this.run = run;
        }

        @Override
        public void run() {
            Canvas c;
            while (run) {
                c = null;

                //limit frame rate to max 60fps
                timeNow = System.currentTimeMillis();
                timeDelta = timeNow - timePrevFrame;
                if (timeDelta < 16) {
                    try {
                        Thread.sleep(16 - timeDelta);
                    } catch (InterruptedException e) {

                    }
                }
                timePrevFrame = System.currentTimeMillis();

                try {
                    c = surfaceHolder.lockCanvas(null);
                    synchronized (surfaceHolder) {
                        //call methods to draw and process next fame
                        gameView.onDraw(c);
                    }
                } finally {
                    if (c != null) {
                        surfaceHolder.unlockCanvasAndPost(c);
                    }
                }
            }
        }
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            scaleFactor *= detector.getScaleFactor();
            scaleFactor = Math.max(MIN_ZOOM, Math.min(scaleFactor, MAX_ZOOM));
            return true;
        }
    }
}
