package com.beta.app;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SurfaceViewActivity extends Activity {
    BallBounces ball;
    private History history;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ball = new BallBounces(this);
        history = new History();
        RelativeLayout layout = new RelativeLayout(this);
        layout.addView(ball);
        View buttons = LayoutInflater.from(this).inflate(R.layout.fragment_selectors, layout, false);
        buttons.findViewById(R.id.leftHand).setOnClickListener(new LimbHelper(LimbEnum.LEFT_HAND, ball));
        buttons.findViewById(R.id.rightHand).setOnClickListener(new LimbHelper(LimbEnum.RIGHT_HAND, ball));
        buttons.findViewById(R.id.leftFoot).setOnClickListener(new LimbHelper(LimbEnum.LEFT_FOOT, ball));
        buttons.findViewById(R.id.rightFoot).setOnClickListener(new LimbHelper(LimbEnum.RIGHT_FOOT, ball));
        buttons.findViewById(R.id.commit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                history.push(Body.fromClimber(ball.body));
            }
        });
        buttons.findViewById(R.id.back).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ball.setState(history.back());
            }
        });
        buttons.findViewById(R.id.next).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ball.setState(history.next());
            }
        });
        layout.addView(buttons);
        setContentView(layout);
    }
}

class Position {
    final int x;
    final int y;

    Position(int x, int y) {
        this.x = x;
        this.y = y;
    }
}

class Body {
    final Position leftHand;
    final Position rightHand;
    final Position leftFoot;
    final Position rightFoot;
    final List<Position> all;

    static Body fromClimber(Climber c) {
        return new Body(
                new Position(c.leftHand.posX, c.leftHand.posY),
                new Position(c.rightHand.posX, c.rightHand.posY),
                new Position(c.leftFoot.posX, c.leftFoot.posY),
                new Position(c.rightFoot.posX, c.rightFoot.posY)
        );
    }

    Body(Position leftHand, Position rightHand, Position leftFoot, Position rightFoot) {
        this.leftHand = leftHand;
        this.rightHand = rightHand;
        this.leftFoot = leftFoot;
        this.rightFoot = rightFoot;
        this.all = Arrays.asList(leftHand, rightHand, leftFoot, rightFoot);
    }
}

class History {
    private final List<Body> positions = new ArrayList<>();

    private int currIndex = 0;

    void push(Body nextMove) {
        positions.add(nextMove);
        currIndex = positions.size()-1;
    }

    Body back() {
        if (currIndex != 0) {
            currIndex--;
            return positions.get(currIndex);
        }
        return positions.get(0);
    }

    Body next() {
        if (currIndex != positions.size()-1) {
            currIndex++;
            return positions.get(currIndex);
        }
        return positions.get(positions.size()-1);
    }
}

class BallBounces extends SurfaceView implements SurfaceHolder.Callback {
    private static final int NONE = 0;
    private static final int DRAG = 1;
    private static final int ZOOM = 2;
    static final int ballRadius = 20;

    GameThread thread;
    int screenW; //Device's screen width.
    int screenH; //Device's screen height.
    int initialY;
    int bgrW;
    int bgrH;
    Bitmap bgr;
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

    final Climber body = Climber.standard();

    LimbEnum selectedLimb;

    public BallBounces(Context context) {
        super(context);
        bgr = BitmapFactory.decodeResource(getResources(), R.drawable.wall1); //Load a background.

        //Initialise animation variables.
        initialY = 100; //Initial vertical position

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
    }

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
                if (event.getPointerCount() == 2) {
                    // panning
                    translateX = event.getX() - startX;
                    translateY = event.getY() - startY;
                }

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
                float adjustedX = event.getX() - previousTranslateX - ballRadius;
                float adjustedY = event.getY() - previousTranslateY - ballRadius;

                for (Limb l : body.all) {
                    if (l.containsPoints(adjustedX / scaleFactor, adjustedY / scaleFactor)
                            || l.id == selectedLimb) {
                        selectedLimb = l.id;
                        l.setX((int) (adjustedX / scaleFactor));
                        l.setY((int) (adjustedY / scaleFactor));
                    }
                }

                ballFingerMove = true;
                break;
            }

            case MotionEvent.ACTION_MOVE: {
                if (event.getPointerCount() == 1) {
                    float adjustedX = (event.getX() - previousTranslateX) - ballRadius;
                    float adjustedY = (event.getY() - previousTranslateY) - ballRadius;
                    for (Limb l : body.all) {
                        if (l.containsPoints(adjustedX / scaleFactor, adjustedY / scaleFactor)
                                || l.id == selectedLimb) {
                            l.setX((int) (adjustedX / scaleFactor));
                            l.setY((int) (adjustedY / scaleFactor));
                        }
                    }
                }
                break;
            }

            case MotionEvent.ACTION_UP:
                ballFingerMove = false;
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

        //Draw background
        canvas.drawColor(Color.BLACK);
        Rect r = new Rect(0, 0, bgrW, bgrH);
        canvas.drawBitmap(bgr, r, r, null);

        // draw limbs
        for (Limb l : body.all) {
            if (l.id == selectedLimb) {
                Paint border = new Paint();
                border.setColor(Color.WHITE);
                border.setAntiAlias(true);
                border.setAlpha(150);
                canvas.drawCircle(l.posX, l.posY, (int) (l.radius * 1.25), border);
                canvas.drawCircle(l.posX, l.posY, l.radius, l.color);
            } else {
                canvas.drawCircle(l.posX, l.posY, l.radius, l.color);
            }
        }

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

    public void setState(Body b) {
        this.body.leftHand.posX = b.leftHand.x;
        this.body.leftHand.posY = b.leftHand.y;

        this.body.rightHand.posX = b.rightHand.x;
        this.body.rightHand.posY = b.rightHand.y;

        this.body.leftFoot.posX = b.leftFoot.x;
        this.body.leftFoot.posY = b.leftFoot.y;

        this.body.rightFoot.posX = b.rightFoot.x;
        this.body.rightFoot.posY = b.rightFoot.y;
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
                if (timeDelta < 32) {
                    try {
                        Thread.sleep(32 - timeDelta);
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

final class Limb {
    final int radius;
    final Paint color;
    final LimbEnum id;

    // position is mutable so it can move
    int posX;
    int posY;

    Limb(LimbEnum id, int radius, Paint color, int posX, int posY) {
        this.id = id;
        this.radius = radius;
        this.color = color;
        this.posX = posX;
        this.posY = posY;
    }

    void setX(int x) {
        this.posX = x;
    }

    void setY(int y) {
        this.posY = y;
    }

    boolean containsPoints(float x, float y) {
        return x <= posX + BallBounces.ballRadius && x >= posX - BallBounces.ballRadius
                && y <= posY + BallBounces.ballRadius && y >= posY - BallBounces.ballRadius;

    }
}

final class Climber {
    final Limb leftHand;
    final Limb rightHand;
    final Limb leftFoot;
    final Limb rightFoot;
    final List<Limb> all;

    private Climber(Limb leftHand, Limb rightHand, Limb leftFoot, Limb rightFoot) {
        this.leftHand = leftHand;
        this.rightHand = rightHand;
        this.leftFoot = leftFoot;
        this.rightFoot = rightFoot;
        all = Arrays.asList(leftHand, rightHand, leftFoot, rightFoot);
    }

    static Climber standard() {
        // left hand
        Paint lH = new Paint();
        lH.setAntiAlias(true);
        lH.setColor(Color.RED);
        lH.setAlpha(90);
        // right hand
        Paint rH = new Paint();
        rH.setAntiAlias(true);
        rH.setColor(Color.BLUE);
        rH.setAlpha(90);
        // left foot
        Paint lF = new Paint();
        lF.setAntiAlias(true);
        lF.setColor(Color.YELLOW);
        lF.setAlpha(90);
        // right foot
        Paint rF = new Paint();
        rF.setAntiAlias(true);
        rF.setColor(Color.GREEN);
        rF.setAlpha(90);

        return new Climber(
                new Limb(LimbEnum.LEFT_HAND, BallBounces.ballRadius, lH, BallBounces.ballRadius * 8, BallBounces.ballRadius * 2),
                new Limb(LimbEnum.RIGHT_HAND, BallBounces.ballRadius, rH, BallBounces.ballRadius * 10, BallBounces.ballRadius * 2),
                new Limb(LimbEnum.LEFT_FOOT, BallBounces.ballRadius, lF, BallBounces.ballRadius * 4, BallBounces.ballRadius * 2),
                new Limb(LimbEnum.RIGHT_FOOT, BallBounces.ballRadius, rF, BallBounces.ballRadius * 6, BallBounces.ballRadius * 2)
        );
    }
}

class LimbHelper implements View.OnClickListener {
    private final LimbEnum l;
    private final BallBounces route;

    LimbHelper(LimbEnum l, BallBounces route) {
        this.l = l;
        this.route = route;
    }

    @Override
    public void onClick(View view) {
        if (route.selectedLimb == l) {
            route.selectedLimb = LimbEnum.NONE;
        } else {
            route.selectedLimb = l;
        }
    }
}

enum LimbEnum {
    LEFT_HAND, RIGHT_HAND, LEFT_FOOT, RIGHT_FOOT, NONE;
}