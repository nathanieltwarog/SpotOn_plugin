import ij.*;
import ij.process.*;
import ij.gui.*;
import ij.plugin.frame.RoiManager;
import ij.gui.OvalRoi;
import ij.measure.ResultsTable;
import ij.io.FileSaver;
import ij.io.SaveDialog;
import java.awt.*;
import java.awt.event.*;
import javax.swing.BoxLayout;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JFrame;
import javax.swing.table.DefaultTableModel;
import javax.swing.ListSelectionModel;
import javax.swing.event.*;
import ij.plugin.filter.*;

public class SpotOn_plugin implements PlugInFilter {
    ImagePlus imp;
    ImagePlus workimp;
    ImageProcessor workip;
    static int echoRows = 130;
    static int echoCols = 202;
    static double scannerScaling = 0.996;
    static double scannerCenter = 5400.0;
    private double echoHeightPx = 4771.6535;
    private double cellheight = echoHeightPx/echoCols;
    private double echoWidthPx = 3070.8661;
    private double cellwidth = echoWidthPx/echoRows;
    private double slideSpacing = 1272.0;
    static double slideWidth = 3539.0;
    static double slideHeight = 1134.0;
    private Parameters parameters;
    private int counter = 0;
    private double imheight, imwidth;
    private WeightedPoint[] slides;
    private Direction scannerEdge = Direction.LEFT;
    private PlaneTransform echoTransform;
    private int wellIndex = 1;
    private int nextSlide = 0;
    private int[] bdbox = new int[4];
    private ResultsTable allTable = new ResultsTable();
    private ImagePlus[] outputImages = new ImagePlus[4];
    

    public int setup(String arg, ImagePlus imp) {
        this.imp = imp;
        return DOES_ALL;
    }

    public void run(ImageProcessor ip) {
        double[] newCoords = new double[]{0,0,0};
        parameters = new Parameters();
        
        workip = ip.duplicate();
        imwidth = (double) workip.getWidth();
        imheight = (double) workip.getHeight();
        
        scannerEdge = Direction.LEFT;
        boolean changed = false;
        if (imwidth>imheight) {
            workip = workip.rotateLeft();
            imwidth = (double) workip.getWidth();
            imheight = (double) workip.getHeight();
            scannerEdge = Direction.BOTTOM;
            changed = true;
        }
        if (imwidth>6000) {
            workip = workip.resize((int) (imwidth/2.0));
            imwidth = (double) workip.getWidth();
            imheight = (double) workip.getHeight();
            changed = true;
        } else if (imwidth<3000) {
            workip = workip.resize((int) (imwidth*2.0));
            imwidth = (double) workip.getWidth();
            imheight = (double) workip.getHeight();
            changed = true;
        }
        workimp = new ImagePlus("Working Image",workip);
        workimp.show();
        
        switch (scannerEdge) {
            case TOP:
                echoHeightPx *= scannerScaling;
                cellheight *= scannerScaling;
                echoWidthPx *= scannerScaling;
                cellwidth *= scannerScaling;
                break;
            case BOTTOM:
                echoHeightPx *= scannerScaling;
                cellheight *= scannerScaling;
                echoWidthPx *= scannerScaling;
                cellwidth *= scannerScaling;
                break;
            case LEFT:
                echoHeightPx *= scannerScaling;
                cellheight *= scannerScaling;
                echoWidthPx *= scannerScaling;
                cellwidth *= scannerScaling;
                break;
            case RIGHT:
                echoHeightPx *= scannerScaling;
                cellheight *= scannerScaling;
                echoWidthPx *= scannerScaling;
                cellwidth *= scannerScaling;
                break;
        }
        
        /*int startx, starty, endx, endy;
        double[] fidlocA, fidlocC, fidlocF, fidlocH;
        ImagePlus kimp;
        ImageProcessor kip, subip;
        try {
            kimp = IJ.openImage(IJ.getDirectory("plugins")+"templates/holder-1.png");
        } catch (Exception e) {
            kimp = new ImagePlus();
            IJ.log("Could not open template!");
        }
        startx = (int) Math.floor(5.0*imwidth/8.0);
        endx = (int) Math.ceil(7.0*imwidth/8.0);
        starty = Math.max(0, (int) Math.floor(((double) (10+16+(0-1)*32))*imheight/153.0));
        endy = (int) Math.ceil(((double) (10+16+0*32))*imheight/153.0);
        subip = workip.duplicate();
        subip.setRoi(startx,starty,endx-startx,endy-starty);
        subip = subip.crop();
        kip = kimp.getProcessor();
        fidlocA = alignTemplate(subip,kip);
        
        try {
            kimp = IJ.openImage(IJ.getDirectory("plugins")+"templates/holder-2.png");
        } catch (Exception e) {
            kimp = new ImagePlus();
            IJ.log("Could not open template!");
        }
        startx = (int) Math.floor(3.0*imwidth/8.0);
        endx = (int) Math.ceil(5.0*imwidth/8.0);
        starty = (int) Math.floor(((double) (10+16+(3+0)*32))*imheight/153.0);
        endy = Math.min((int) imheight, (int) Math.ceil(((double) (10+16+(3+1)*32))*imheight/153.0));
        subip = workip.duplicate();
        subip.setRoi(startx,starty,endx-startx,endy-starty);
        subip = subip.crop();
        kip = kimp.getProcessor();
        fidlocH = alignTemplate(subip,kip);*/
        
        if (scannerEdge == Direction.BOTTOM && parameters.holder) {
            newCoords = locateHolder(workip);
        } else { parameters.holder = false; }
        
        double pCenterX, pCenterY, plateAngle;
        int slot;
        if (parameters.ignoreSlides) {
            double sX, sY;
            double cA, sA;
            pCenterX = newCoords[0];
            pCenterY = newCoords[1];
            plateAngle = newCoords[2];
            cA = Math.cos(plateAngle);
            sA = Math.sin(plateAngle);
            
            slides = new WeightedPoint[4];
            for (slot=0; slot<4; slot++) {
                sX = pCenterX+sA*(1.5-slot)*slideSpacing;
                sY = pCenterY-cA*(1.5-slot)*slideSpacing;
                slides[slot] = new WeightedPoint(sX,sY,1.0);
            }
        } else {
            slides = locateSlides(workip,parameters.intellislide);
            boolean any = false, all = true;
            for (slot=0; slot<4; slot++) {
                if (slides[slot].getWeight()<=0.8) { all = false; }
                else { any = true; }
            }
            if (!all) {
                ImageProcessor flipip = workip.duplicate();
                flipip = flipip.rotateLeft().rotateLeft();
                WeightedPoint[] altslides = locateSlides(flipip,parameters.intellislide);
                if (!any) {
                    slides = altslides;
                    workip = flipip;
                    workimp.setProcessor(workip);
                    workimp.show();
                    if (parameters.holder) {
                        newCoords[0] = imwidth-newCoords[0];
                        newCoords[1] = imheight-newCoords[1];
                    }
                    switch (scannerEdge) {
                        case TOP:
                            scannerEdge = Direction.BOTTOM;
                            break;
                        case RIGHT:
                            scannerEdge = Direction.LEFT;
                            break;
                        case BOTTOM:
                            scannerEdge = Direction.TOP;
                            break;
                        case LEFT:
                            scannerEdge = Direction.RIGHT;
                            break;
                    }
                } else {
                    for (slot=0; slot<4; slot++) {
                        if (slides[slot].getWeight()<=0.8 && altslides[3-slot].getWeight()>0.8) {
                            slides[slot] = altslides[3-slot];
                        }
                    }
                }
            }
        }
        
        if (parameters.holder) {
            pCenterX = newCoords[0];
            pCenterY = newCoords[1];
            plateAngle = newCoords[2];
        } else {
            double xmn=0, ymn=0, x2mn=0, y2mn=0, xymn=0, sct=0;
            for (slot=0; slot<4; slot++) {
                if (slides[slot].getWeight()<=0.8) { continue; }
                sct += 1;
                xmn += slides[slot].getX();
                ymn += slides[slot].getY();
                x2mn += slides[slot].getX()*slides[slot].getX();
                xymn += slides[slot].getX()*slides[slot].getY();
                y2mn += slides[slot].getY()*slides[slot].getY();
            }
            double cA, sA;
            double xvar, yvar, xycov;
            if (sct>1) {
                xmn /= sct;
                ymn /= sct;
                x2mn /= sct;
                xymn /= sct;
                y2mn /= sct;
                
                xvar = x2mn - (xmn*xmn);
                yvar = y2mn - (ymn*ymn);
                xycov = xymn - (xmn*ymn);
                plateAngle = Math.atan2(-2*xycov,yvar-xvar)/2;
                cA = Math.cos(plateAngle);
                sA = Math.sin(plateAngle);
            } else { 
                plateAngle = 0.0;
                cA = 1.0;
                sA = 0.0;
            }
            
            if (parameters.intellislide) {
                switch (scannerEdge) {
                    case TOP:
                        slideSpacing *= scannerScaling;
                        break;
                    case BOTTOM:
                        slideSpacing *= scannerScaling;
                        break;
                }
            }

            pCenterX = 0;
            pCenterY = 0;
            for (slot=0; slot<4; slot++) {
                if (slides[slot].getWeight()<=0.8) { continue; }
                pCenterX += slides[slot].getX()-sA*(1.5-slot)*slideSpacing;
                pCenterY += slides[slot].getY()+cA*(1.5-slot)*slideSpacing;
            }
            pCenterX /= sct;
            pCenterY /= sct;
            
            if (!parameters.intellislide) {
                double newcent;
                switch (scannerEdge) {
                    case TOP:
                        newcent = scannerCenter;
                        pCenterY = newcent+(pCenterY-newcent)*scannerScaling;
                        break;
                    case BOTTOM:
                        newcent = imheight-scannerCenter;
                        pCenterY = newcent+(pCenterY-newcent)*scannerScaling;
                        break;
                    case RIGHT:
                        newcent = imwidth-scannerCenter;
                        pCenterX = newcent+(pCenterX-newcent)*scannerScaling;
                        break;
                    case LEFT:
                        newcent = scannerCenter;
                        pCenterX = newcent+(pCenterX-newcent)*scannerScaling;
                        break;
                }
            }
            
            pCenterX += 24;
            pCenterY += (-30 + 1.5*cellheight);
        }
        
        float[] pCornerX = {
            (float) (pCenterX+Math.cos(plateAngle)*(echoWidthPx/2.0)-Math.sin(plateAngle)*(echoHeightPx/2.0)),
            (float) (pCenterX-Math.cos(plateAngle)*(echoWidthPx/2.0)+Math.sin(plateAngle)*(echoHeightPx/2.0)),
            (float) (pCenterX-Math.cos(plateAngle)*(echoWidthPx/2.0)+Math.sin(plateAngle)*(echoHeightPx/2.0)),
            (float) (pCenterX+Math.cos(plateAngle)*(echoWidthPx/2.0)-Math.sin(plateAngle)*(echoHeightPx/2.0))
        };
        float[] pCornerY = {
            (float) (pCenterY+Math.sin(plateAngle)*(echoWidthPx/2.0)+Math.cos(plateAngle)*(echoHeightPx/2.0)),
            (float) (pCenterY+Math.sin(plateAngle)*(echoWidthPx/2.0)+Math.cos(plateAngle)*(echoHeightPx/2.0)),
            (float) (pCenterY-Math.sin(plateAngle)*(echoWidthPx/2.0)-Math.cos(plateAngle)*(echoHeightPx/2.0)),
            (float) (pCenterY-Math.sin(plateAngle)*(echoWidthPx/2.0)-Math.cos(plateAngle)*(echoHeightPx/2.0))
        };

        FloatPolygon platePoly = new FloatPolygon(pCornerX,pCornerY);
        PolygonRoi plateRoi = new PolygonRoi(platePoly,Roi.POLYGON);

        echoTransform = new PlaneTransform(pCornerX[1],pCornerY[1],plateAngle,cellwidth,cellheight);
        
        ImageProcessor newip = workip.duplicate();
        ImagePlus newimp = new ImagePlus("ROI",newip);
        workimp.setRoi(plateRoi);
        workimp.show();
        
        showNextSlide();
    }
    
    private double[] locateHolder(ImageProcessor thisip) {
        int slot=0;
        int startx, starty, endx, endy;
        double[] hlocA1, hlocA2, hlocA3;
        double[] hlocB1, hlocB2, hlocB3;
        WeightedPoint[] alocs = new WeightedPoint[3];
        WeightedPoint[] blocs = new WeightedPoint[3];
        WeightedPoint aloc, bloc;
        ImagePlus kimp;
        ImageProcessor kip, subip;
        try {
            kimp = IJ.openImage(IJ.getDirectory("plugins")+"templates/holder-1.png");
        } catch (Exception e) {
            kimp = new ImagePlus();
            IJ.log("Could not open template!");
        }
        starty = Math.max(0, (int) Math.floor(((double) (10+16+(0-1)*32))*imheight/153.0));
        endy = (int) Math.ceil(((double) (10+16+0*32))*imheight/153.0);
        
        for (slot=0; slot<3; slot++) {
            startx = (int) Math.floor(((double)(2*slot+1))*imwidth/8.0);
            endx = (int) Math.ceil(((double)(2*slot+3))*imwidth/8.0);
            subip = thisip.duplicate();
            subip.setRoi(startx,starty,endx-startx,endy-starty);
            subip = subip.crop();
            kip = kimp.getProcessor();
            hlocA1 = alignTemplate(subip,kip);
            alocs[slot] = new WeightedPoint(
                hlocA1[0] + (double)(startx+975*(1-slot)),
                hlocA1[1] + (double)(starty),
                hlocA1[2]
            );
        }
        
        try {
            kimp = IJ.openImage(IJ.getDirectory("plugins")+"templates/holder-2.png");
        } catch (Exception e) {
            kimp = new ImagePlus();
            IJ.log("Could not open template!");
        }
        starty = (int) Math.floor(((double) (10+16+(3+0)*32))*imheight/153.0);
        endy = Math.min((int) imheight, (int) Math.ceil(((double) (10+16+(3+1)*32))*imheight/153.0));
        
        for (slot=0; slot<3; slot++) {
            startx = (int) Math.floor(((double)(2*slot+1))*imwidth/8.0);
            endx = (int) Math.ceil(((double)(2*slot+3))*imwidth/8.0);
            subip = thisip.duplicate();
            subip.setRoi(startx,starty,endx-startx,endy-starty);
            subip = subip.crop();
            kip = kimp.getProcessor();
            hlocB1 = alignTemplate(subip,kip);
            blocs[slot] = new WeightedPoint(
                hlocB1[0] + (double)(startx+975*(1-slot)),
                hlocB1[1] + (double)(starty),
                hlocB1[2]
            );
        }
        
        aloc = weightedCenter(alocs);
        bloc = weightedCenter(blocs);
        
        double pCenterX = (aloc.getX()+bloc.getX())/2;
        double pCenterY = (aloc.getY()+bloc.getY())/2;
        double newAngle = Math.atan2(-(bloc.getX()-aloc.getX()),(bloc.getY()-aloc.getY()));
        pCenterX += 84*Math.cos(newAngle) - 8*Math.sin(newAngle);
        pCenterY += 84*Math.sin(newAngle) + 8*Math.cos(newAngle);
        double[] newCoords = new double[]{ pCenterX, pCenterY, newAngle };
        
        return(newCoords);
    }
    
    private WeightedPoint[] locateSlides(ImageProcessor thisip, boolean intellislides) {
        if (intellislides) {
            return locateIntellislides(thisip);
        } else {
            return locateHomegrownSlides(thisip);
        }
    }
    
    private WeightedPoint[] locateIntellislides(ImageProcessor thisip) {
        int slot;
        WeightedPoint[] box = new WeightedPoint[3];
        WeightedPoint[] subbox = new WeightedPoint[2];
        WeightedPoint temp;
        double[] fidlocA, fidlocB, fidlocC;
        int startx, endx, starty, endy;
        ImagePlus kimp;
        ImageProcessor kip, subip;
        
        WeightedPoint[] curslides = new WeightedPoint[4];
        
        for (slot=0; slot<4; slot++) {
            try {
                kimp = IJ.openImage(IJ.getDirectory("plugins")+"templates/bruker.png");
            } catch (Exception e) {
                kimp = new ImagePlus();
                IJ.log("Could not open template!");
            }
            startx = (int) Math.floor(5.0*imwidth/6.0);
            endx = (int) imwidth;
            starty = Math.max(0, (int) Math.floor(((double) (10+40+(slot-1)*32))*imheight/153.0));
            endy = Math.min((int) imheight, (int) Math.ceil(((double) (10+40+slot*32))*imheight/153.0));
            subip = thisip.duplicate();
            subip.setRoi(startx,starty,endx-startx,endy-starty);
            subip = subip.crop();
            kip = kimp.getProcessor();
            fidlocA = alignTemplate(subip,kip);
            box[0] = new WeightedPoint(
                (double)startx + fidlocA[0] + 145.0 - slideWidth/2,
                (double)starty + fidlocA[1] + 235.0 - slideHeight/2,
                Math.max(4.0*Math.pow(fidlocA[2],2.0)-1.0,0.0)/2
            );
            subbox[0] = box[0];
            
            try {
                kimp = IJ.openImage(IJ.getDirectory("plugins")+"templates/medallion.png");
            } catch (Exception e) {
                kimp = new ImagePlus();
                IJ.log("Could not open template!");
            }
            startx = (int) Math.floor(1.0*imwidth/3.0);
            endx = (int) Math.ceil(1.0*imwidth/2.0);
            starty = Math.max(0, (int) Math.floor(((double) (10+16+(slot-1)*32))*imheight/153.0));
            endy = (int) Math.ceil(((double) (10+16+slot*32))*imheight/153.0);
            subip = thisip.duplicate();
            subip.setRoi(startx,starty,endx-startx,endy-starty);
            subip = subip.crop();
            kip = kimp.getProcessor();
            fidlocB = alignTemplate(subip,kip);
            box[1] = new WeightedPoint(
                (double)startx + fidlocB[0] - 1412.0 + slideWidth/2,
                (double)starty + fidlocB[1] - 75.0  + slideHeight/2,
                Math.max(4.0*Math.pow(fidlocB[2],2.0)-1.0,0.0)/2
            );
            subbox[1] = box[1];
            
            temp = weightedCenter(subbox);
            
            startx = (int) Math.floor(0.0*imwidth/4.0);
            endx = (int) Math.ceil(1.0*imwidth/4.0);
            starty = Math.max(0, (int) Math.floor(temp.getY()+75.0+slideHeight/2-slideSpacing/2.0));
            endy = Math.min((int) imheight, (int) Math.ceil(temp.getY()+75.0+slideHeight/2));
            subip = thisip.duplicate();
            subip.setRoi(startx,starty,endx-startx,endy-starty);
            subip = subip.crop();
            kip = kimp.getProcessor();
            fidlocC = alignTemplate(subip,kip);
            box[2] = new WeightedPoint(
                (double)startx + fidlocC[0] - 230.0 + slideWidth/2,
                (double)starty + fidlocC[1] + 85.0  - slideHeight/2,
                Math.max(4.0*Math.pow(fidlocC[2],2.0)-1.0,0.0)/2
            );
            
            curslides[slot] = weightedCenter(box);
        }
        
        return curslides;
    }
    
    private WeightedPoint[] locateHomegrownSlides(ImageProcessor thisip) {
        int slot;
        WeightedPoint[] box = new WeightedPoint[4];
        double[] fidlocA, fidlocC, fidlocF, fidlocH;
        int startx, endx, starty, endy;
        ImagePlus kimp;
        ImageProcessor kip, subip;
        
        WeightedPoint[] curslides = new WeightedPoint[4];
        
        for (slot=0; slot<4; slot++) {
            
            try {
                kimp = IJ.openImage(IJ.getDirectory("plugins")+"templates/templateA_dark.png");
            } catch (Exception e) {
                kimp = new ImagePlus();
                IJ.log("Could not open template!");
            }
            startx = (int) Math.floor(imwidth/2.0);
            endx = (int) Math.ceil(5.0*imwidth/6.0);
            starty = Math.max(0, (int) Math.floor(((double) (10+16+(slot-1)*32))*imheight/153.0));
            endy = (int) Math.ceil(((double) (10+16+slot*32))*imheight/153.0);
            subip = thisip.duplicate();
            subip.setRoi(startx,starty,endx-startx,endy-starty);
            subip = subip.crop();
            kip = kimp.getProcessor();
            fidlocA = alignTemplate(subip,kip);
            box[0] = new WeightedPoint(
                (double)startx + fidlocA[0] - 2442.0 + 3539.0/2,
                (double)starty + fidlocA[1] - 120.0  + 1134.0/2,
                Math.max(4.0*Math.pow(fidlocA[2],2.0)-1.0,0.0)
            );
            
            try {
                kimp = IJ.openImage(IJ.getDirectory("plugins")+"templates/templateC_dark.png");
            } catch (Exception e) {
                kimp = new ImagePlus();
                IJ.log("Could not open template!");
            }
            startx = (int) Math.floor(imwidth/2.0);
            endx = (int) Math.ceil(5.0*imwidth/6.0);
            starty = (int) Math.floor(((double) (10+16+(slot+0)*32))*imheight/153.0);
            endy = Math.min((int) imheight, (int) Math.ceil(((double) (10+16+(slot+1)*32))*imheight/153.0));
            subip = thisip.duplicate();
            subip.setRoi(startx,starty,endx-startx,endy-starty);
            subip = subip.crop();
            kip = kimp.getProcessor();
            fidlocC = alignTemplate(subip,kip);
            box[1] = new WeightedPoint(
                (double)startx + fidlocC[0] - 2436.0 + 3539.0/2,
                (double)starty + fidlocC[1] - 1032.0  + 1134.0/2,
                Math.max(4.0*Math.pow(fidlocC[2],2.0)-1.0,0.0)
            );
            
            try {
                kimp = IJ.openImage(IJ.getDirectory("plugins")+"templates/templateF_dark.png");
            } catch (Exception e) {
                kimp = new ImagePlus();
                IJ.log("Could not open template!");
            }
            startx = (int) Math.floor(imwidth/6.0);
            endx = (int) Math.ceil(imwidth/2.0);
            starty = Math.max(0, (int) Math.floor(((double) (10+16+(slot-1)*32))*imheight/153.0));
            endy = (int) Math.ceil(((double) (10+16+slot*32))*imheight/153.0);
            subip = thisip.duplicate();
            subip.setRoi(startx,starty,endx-startx,endy-starty);
            subip = subip.crop();
            kip = kimp.getProcessor();
            fidlocF = alignTemplate(subip,kip);
            box[2] = new WeightedPoint(
                (double)startx + fidlocF[0] - 1074.0 + 3539.0/2,
                (double)starty + fidlocF[1] - 117.0  + 1134.0/2,
                Math.max(4.0*Math.pow(fidlocF[2],2.0)-1.0,0.0)
            );
            
            try {
                kimp = IJ.openImage(IJ.getDirectory("plugins")+"templates/templateH_dark.png");
            } catch (Exception e) {
                kimp = new ImagePlus();
                IJ.log("Could not open template!");
            }
            startx = (int) Math.floor(imwidth/6.0);
            endx = (int) Math.ceil(imwidth/2.0);
            starty = (int) Math.floor(((double) (10+16+(slot+0)*32))*imheight/153.0);
            endy = Math.min((int) imheight, (int) Math.ceil(((double) (10+16+(slot+1)*32))*imheight/153.0));
            subip = thisip.duplicate();
            subip.setRoi(startx,starty,endx-startx,endy-starty);
            subip = subip.crop();
            kip = kimp.getProcessor();
            fidlocH = alignTemplate(subip,kip);
            box[3] = new WeightedPoint(
                (double)startx + fidlocH[0] - 1068.0 + 3539.0/2,
                (double)starty + fidlocH[1] - 1026.0  + 1134.0/2,
                Math.max(4.0*Math.pow(fidlocH[2],2.0)-1.0,0.0)
            );
            
            curslides[slot] = weightedCenter(box);
        }
        
        return curslides;
    }
    
    private void showNextSlide() {
        int slot;
        slot = nextSlide;
        nextSlide++;
        while (slot<4 && slides[slot].getWeight()<0.8) {
            slot = nextSlide;
            nextSlide++;
        }
        
        if (slot<4) {
            ImageProcessor workip, subip;
            ImagePlus subimp;
            ImageCanvas subcanv;
            
            workip = workimp.getProcessor();
            bdbox = new int[]{Math.max((int) Math.floor(slides[slot].getX()-slideWidth/2.0),0),
                     Math.max((int) Math.floor(slides[slot].getY()-slideHeight/2.0),0),
                     (int) Math.min(slideWidth,(imwidth-Math.floor(slides[slot].getX()-slideWidth/2))),
                     (int) Math.min(slideHeight,(imheight-Math.floor(slides[slot].getY()-slideHeight/2)))};
            
            subip = workip.duplicate();
            subip.setRoi(bdbox[0],bdbox[1],bdbox[2],bdbox[3]);
            subip = subip.crop();
            subimp = new ImagePlus("Expanded",subip);
            
            subcanv = new ImageCanvas(subimp);
            new CustomWindow(subimp,subcanv);
        } else {
            boolean any = false;
            for (slot=0; slot<4; slot++) {
                if (slides[slot].getWeight()>0.8) {
                    any = true;
                    break;
                }
            }
            workimp.close();
            if (any) {
                allTable.show("Final Results");
                
                String fname;
                FileSaver fsave;
                SaveDialog imf = new SaveDialog("Save slide images","annotated.png",".png");
                if (imf.getFileName() != null) {
                    for (slot=0; slot<4; slot++) {
                        if (slides[slot].getWeight()<=0.8) { continue; }
                        fname = imf.getDirectory()+imf.getFileName().replace(".png","_slide"+String.valueOf(slot+1)+".png");
                        fsave = new FileSaver(outputImages[slot]);
                        fsave.saveAsPng(fname);
                    }
                }
            }
        }
    }
    
    private void printArray(double[] values) {
        String message;
        if (values.length>0) {
            message = String.valueOf(values[0]);
            if (values.length>1) {
                for (int i=1; i<values.length; i++) {
                    message += ", "+String.valueOf(values[i]);
                }
            }
        } else {
            message = "<Empty>";
        }
        IJ.log(message);
    }
    
    enum Direction {
        TOP,
        RIGHT,
        BOTTOM,
        LEFT
    }
    
    class WeightedPoint {
        private double x;
        private double y;
        private double weight;
        
        WeightedPoint(double xin, double yin, double win) {
            x = xin;
            y = yin;
            weight = win;
        }
        
        public double getX() { return x; }
        public double getY() { return y; }
        public double getWeight() { return weight; }
    }
    
    class Point {
        private double x;
        private double y;
        
        Point(double xin, double yin) {
            x = xin;
            y = yin;
        }
        
        public double getX() { return x; }
        public double getY() { return y; }
    }
    
    class PlaneTransform {
        private double[][] fmat;
        private double[][] imat;
        private double[] offset;
        
        PlaneTransform(double cornerx, double cornery, double angle, double xscale, double yscale) {
            fmat = new double[][]{
                {  Math.sin(angle)/xscale, -Math.cos(angle)/yscale },
                {  Math.cos(angle)/xscale,  Math.sin(angle)/yscale }
            };
            imat = new double[][]{
                {  Math.sin(angle)*xscale,  Math.cos(angle)*xscale },
                { -Math.cos(angle)*yscale,  Math.sin(angle)*yscale }
            };
            offset = new double[]{
                -(fmat[0][0]*cornerx + fmat[0][1]*cornery),
                -(fmat[1][0]*cornerx + fmat[1][1]*cornery )
            };
        }
        
        public Point transform(Point p) {
            double oldx, oldy, newx, newy;
            oldx = p.getX();
            oldy = p.getY();
            newx = fmat[0][0]*oldx + fmat[0][1]*oldy + offset[0];
            newy = fmat[1][0]*oldx + fmat[1][1]*oldy + offset[1];
            return new Point(newx,newy);
        }
        
        public Point invert(Point p) {
            double oldx, oldy, newx, newy;
            newx = p.getX() - offset[0];
            newy = p.getY() - offset[1];
            oldx = imat[0][0]*newx + imat[0][1]*newy;
            oldy = imat[1][0]*newx + imat[1][1]*newy;
            return new Point(oldx,oldy);
        }
    }
    
    class Parameters {
        public String sourcePlateName;
        public String destinationPlateName;
        public double transferVolume;
        public String defaultComment;
        public boolean intellislide;
        public boolean holder;
        public boolean ignoreSlides;
        
        Parameters() {
            GenericDialog gd = new GenericDialog("Basic Parameters");
            gd.addStringField("Source Plate Name", "drug");
            gd.addStringField("Destination Plate Name","JBMB_Tissue01");
            gd.addNumericField("Transfer Volume (nL)", 2.5);
            gd.addStringField("Default Well Comment","");
            gd.addCheckbox("Using Bruker Intellislides?", false);
            gd.addCheckbox("Locate holder directly?", true);
            gd.addCheckbox("Ignore slide markings?",false);
            gd.showDialog();
            if (gd.wasCanceled()) return;
            
            sourcePlateName = gd.getNextString();
            destinationPlateName = gd.getNextString();
            transferVolume = gd.getNextNumber();
            defaultComment = gd.getNextString();
            intellislide = gd.getNextBoolean();
            holder = gd.getNextBoolean();
            ignoreSlides = gd.getNextBoolean();
            ignoreSlides = ignoreSlides && holder;
        }
    }
    
    class CustomWindow extends ImageWindow implements ActionListener {
        
        private int nrows = 16;
        private int ncols = 24;
        private int nwells = nrows*ncols;
        
        private Button button1, button2;
        private Button upbutton, downbutton;
        private TextField tf, wellfield, spotfield;
        private Checkbox wellCheck;
        private RoiManager roim;
        private MyAdapter mouse;
        private String wellName;
        private String comment = parameters.defaultComment;
        private boolean autoincrement = true;
        private ResultsTable rtable = new ResultsTable();
        private DefaultTableModel model;
        private JTable table;
       
        CustomWindow(ImagePlus imp, ImageCanvas ic) {
            super(imp, ic);
            wellName = getWellName(wellIndex);
            setLayout(new BoxLayout(this,BoxLayout.Y_AXIS));
            roim = new RoiManager();
            roim.runCommand("Show All");
            roim.setVisible(false);
            model = new DefaultTableModel(new Object[0][4], new String[]{"Source Well", "Comment", "X", "Y"});
            addPanel();
            mouse = new MyAdapter();
            ic.addMouseListener(mouse);
        }
    
        void addPanel() {
            Panel wellPanel = new Panel();
            wellPanel.setLayout(new FlowLayout());
            wellPanel.add(new Label("Well:"));
            wellfield = new TextField(wellName,10);
            wellfield.setEditable(false);
            wellPanel.add(wellfield);
            upbutton = new Button("Up");
            upbutton.addActionListener(this);
            downbutton = new Button("Down");
            downbutton.addActionListener(this);
            wellPanel.add(upbutton);
            wellPanel.add(downbutton);
            
            Panel checkPanel = new Panel();
            checkPanel.setLayout(new FlowLayout());
            wellCheck = new Checkbox("Auto-increment well with each point",autoincrement);
            wellCheck.addItemListener(new ItemListener() {
                public void itemStateChanged(ItemEvent e) {
                    autoincrement = wellCheck.getState();
                }
            });
            checkPanel.add(wellCheck);
            
            Panel spotPanel = new Panel();
            spotPanel.setLayout(new FlowLayout());
            spotPanel.add(new Label("Spot Comment:"));
            spotfield = new TextField(comment,30);
            spotfield.addTextListener(new TextListener() {
                public void textValueChanged(TextEvent e) {
                    comment = spotfield.getText();
                }
            });
            spotPanel.add(spotfield);
            
            String closeMessage;
            if (nextSlide<4) { closeMessage = " Next Slide "; }
            else { closeMessage = " Finish "; }
            button2 = new Button(closeMessage);
            button2.addActionListener(this);
            
            Panel controlPanel = new Panel();
            controlPanel.setLayout(new GridLayout(4,1,0,10));
            controlPanel.add(wellPanel);
            controlPanel.add(checkPanel);
            controlPanel.add(spotPanel);
            controlPanel.add(button2);
            
            table = new JTable(model);
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            ListSelectionModel selectionModel = table.getSelectionModel();
            selectionModel.addListSelectionListener(new ListSelectionListener() {
                public void valueChanged(ListSelectionEvent e) {
                    if (table.getSelectedRow()>=0) {
                        roim.select(table.getSelectedRow());
                    }
                }
            });
            
            JScrollPane spane = new JScrollPane(table);
            Dimension tableDims = new Dimension(500,200);
            spane.setPreferredSize(tableDims);
            
            Panel tablePanel = new Panel();
            tablePanel.setLayout(new BoxLayout(tablePanel, BoxLayout.Y_AXIS));
            
            Panel deletePanel = new Panel();
            deletePanel.setLayout(new FlowLayout());
            button1 = new Button(" Delete This Spot ");
            button1.addActionListener(this);
            deletePanel.add(button1);
            
            tablePanel.add(spane);
            tablePanel.add(deletePanel);
            
            Panel superpanel = new Panel();
            superpanel.setLayout(new GridLayout(1, 2, 20, 0));
            superpanel.add(controlPanel);
            superpanel.add(tablePanel);
            add(superpanel);
            pack();
        }
        
        private void incrementWellIndex() {
            changeWellIndex(wellIndex+1);
        }
        private void decrementWellIndex() {
            changeWellIndex(wellIndex-1);
        }
        private void changeWellIndex(int index) {
            if (index<1) { index = 1; }
            else if (index>nwells) { index = nwells; }
            wellIndex = index;
            wellName = getWellName(index);
            wellfield.setText(wellName);
        }
        private String getWellName(int index) {
            int col = (index-1)%ncols + 1;
            int row = (index-1)/ncols + 1;
            return String.valueOf((char) (row+64) )+String.format("%02d",col);
        }
        
        
        public void actionPerformed(ActionEvent e) {
            Object b = e.getSource();
            if (b==button1) {
                if (table.getSelectedRow()>=0) {
                    int removed = table.getSelectedRow();
                    rtable.deleteRow(removed);
                    roim.runCommand("Delete");
                    roim.deselect();
                    model.removeRow(removed);
                    table.clearSelection();
                }
            } else if (b==button2) {
                close();
            } else if (b==upbutton) {
                incrementWellIndex();
            } else if (b==downbutton) {
                decrementWellIndex();
            }
        }
        
        public void storeResults() {
            for (int i=0; i<rtable.size(); i++) {
                allTable.addRow();
                allTable.addValue("Slide",rtable.getStringValue("Slide",i));
                allTable.addValue("ImageX",rtable.getValue("ImageX",i));
                allTable.addValue("ImageY",rtable.getValue("ImageY",i));
                allTable.addValue("Source Plate Name",rtable.getStringValue("Source Plate Name",i));
                allTable.addValue("Source Column",rtable.getStringValue("Source Column",i));
                allTable.addValue("Source Row",rtable.getStringValue("Source Row",i));
                allTable.addValue("Destination Plate Name",rtable.getStringValue("Destination Plate Name",i));
                allTable.addValue("Destination Row",rtable.getStringValue("Destination Row",i));
                allTable.addValue("Destination Col",rtable.getStringValue("Destination Col",i));
                allTable.addValue("Transfer Volume",rtable.getValue("Transfer Volume",i));
                allTable.addValue("Comment",rtable.getStringValue("Comment",i));
            }
        }
        
        public void storeImage() {
            Roi croi;
            ImageProcessor ip = imp.getProcessor();
            ImageProcessor oip = ip.duplicate();
            oip = oip.convertToColorProcessor();
            oip.setColor(Color.red);
            oip.setLineWidth(3);
            for (int i=0; i<roim.getCount(); i++) {
                croi = roim.getRoi(i);
                oip.draw(croi);
            }
            ImagePlus outputImage = new ImagePlus("Output",oip);
            outputImages[nextSlide-1] = outputImage;
        }
        
        public boolean close() {
            storeImage();
            storeResults();
            super.close();
            roim.close();
            showNextSlide();
            return true;
        }
        
        private void addSpot(double icx, double icy, int pr, int pc) {
            for (int i=0; i<rtable.size(); i++) {
                if (Integer.parseInt(rtable.getStringValue("Destination Row",i))==pr &&
                    Integer.parseInt(rtable.getStringValue("Destination Col",i))==pc) {
                    return;
                }
            }
            
            OvalRoi dot = new OvalRoi(icx-(cellwidth/2),icy-(cellheight/2),cellwidth,cellheight);
            roim.deselect();
            roim.addRoi(dot);
            
            rtable.addRow();
            rtable.addValue("Slide",String.valueOf(nextSlide));
            rtable.addValue("ImageX",icx);
            rtable.addValue("ImageY",icy);
            rtable.addValue("Source Plate Name",parameters.sourcePlateName);
            rtable.addValue("Source Column",String.valueOf((wellIndex-1)%ncols + 1));
            rtable.addValue("Source Row",String.valueOf((wellIndex-1)/ncols + 1));
            rtable.addValue("Destination Plate Name",parameters.destinationPlateName);
            rtable.addValue("Destination Row",String.valueOf(pr));
            rtable.addValue("Destination Col",String.valueOf(pc));
            rtable.addValue("Transfer Volume",parameters.transferVolume);
            rtable.addValue("Comment",comment);
            
            table.clearSelection();
            model.addRow(new Object[]{getWellName(wellIndex),comment,icx,icy});
            
            if (autoincrement) { incrementWellIndex(); }
        }
        
        class MyAdapter extends MouseAdapter {
            public void mouseClicked(MouseEvent e) {
                int pr, pc;
                double icx, icy;
                Point echop, icent;
                
                echop = echoTransform.transform(new Point((double)(ic.offScreenX(e.getX())+bdbox[0]),(double)(ic.offScreenY(e.getY())+bdbox[1])));
                pc = ((int) Math.floor(echop.getX()))+1;
                pr = ((int) Math.floor(echop.getY()))+1;

                if (pr>=1 && pc>=1 && pr<=echoRows && pc<=echoCols) {
                    icent = echoTransform.invert(new Point(((double)pc)-0.5,((double)pr)-0.5));
                    icx = icent.getX()-((double)(bdbox[0]));
                    icy = icent.getY()-((double)(bdbox[1]));
                    
                    addSpot(icx,icy,pr,echoCols+1-pc);
                } else {
                    return;
                }
            }
        }
    }
    
    private double[] alignTemplate(ImageProcessor rip, ImageProcessor kip) {
        final int rwidth = rip.getWidth();
        final int rheight = rip.getHeight();
        final int kwidth = kip.getWidth();
        final int kheight = kip.getHeight();
        
        rip = rip.convertToFloat();
        rip.blurGaussian(2);
        kip = kip.convertToFloat();
        kip.blurGaussian(2);
        ImageStatistics stats = kip.getStatistics();
        double kmean = stats.mean;
        double ksd = stats.stdDev/kmean;

        kip.multiply(1/((double) (kmean*kwidth*kheight)));
        ImageProcessor fip = kip.duplicate();
        fip.setValue(1/((double) (kwidth*kheight)));
        fip.fill();

        ImageProcessor f_rexip = fftConvolve(rip,kip);
        ImageProcessor f_frexip = fftConvolve(rip,fip);
        rip.sqr();
        ImageProcessor f_f2rexip = fftConvolve(rip,fip);

        ImageProcessor numip = f_rexip.duplicate();
        numip.copyBits(f_frexip,0,0,Blitter.SUBTRACT);
        f_frexip.sqr();
        ImageProcessor denip = f_f2rexip.duplicate();
        denip.copyBits(f_frexip,0,0,Blitter.SUBTRACT);
        denip.sqrt();
        ImageProcessor corip = numip.duplicate();
        corip.copyBits(denip,0,0,Blitter.DIVIDE);

        int i,j;
        int maxx = -1, maxy = -1;
        double maxv = 0, curv;
        for (i=0; i<rwidth; i++) {
            for (j=0; j<rheight; j++) {
                curv = corip.getPixelValue(i,j);
                if (curv>maxv) {
                    maxx = i;
                    maxy = j;
                    maxv = curv;
                }
            }
        }

        double[] output = {(double) maxx, (double) maxy, maxv/ksd};
        return output;
    }
    
    private ImageProcessor fftConvolve(ImageProcessor rip, ImageProcessor kip) {
        final int rwidth = rip.getWidth();
        final int rheight = rip.getHeight();
        final int kwidth = kip.getWidth();
        final int kheight = kip.getHeight();

        int canvsize = (int) Math.pow(2.0,Math.ceil(Math.log((double) Math.max(rwidth+kwidth,rheight+kheight))/Math.log(2.0)));

        int excanvx = canvsize;
        int excanvy = canvsize;
        int offx = (excanvx-rwidth)/2;
        int offy = (excanvy-rheight)/2;
        int koffx = (excanvx-kwidth)/2;
        int koffy = (excanvy-kheight)/2;

        ImageProcessor exip = rip.createProcessor(excanvx, excanvy);
        exip.setValue(0.0);
        exip.fill();
        exip.insert(rip, offx, offy);
        ImageProcessor exkip = kip.createProcessor(excanvx, excanvy);
        exkip.setValue(0.0);
        exkip.fill();
        exkip.insert(kip, koffx, koffy);

        FHT exip_f = new FHT(exip);
        FHT exkip_f = new FHT(exkip);
        exip_f.transform();
        exkip_f.transform();
        FHT f_rexip = exip_f.conjugateMultiply(exkip_f);
        f_rexip.inverseTransform();
        f_rexip.swapQuadrants();
        f_rexip.resetMinAndMax();
        ImageProcessor rexip = f_rexip.convertToFloat();

        rexip.setRoi(offx,offy,rwidth,rheight);
        rexip = rexip.crop();
        return rexip;
    }
    
    private WeightedPoint weightedCenter(WeightedPoint[] box) {
        int i, iter, npts;
        double sigma = 15.0;
        double sig22 = 2.0*(sigma*sigma);
        double cx, cy, csum, cmax;
        double dx, dy, dt;
        WeightedPoint defout = box[0];
        
        npts = box.length;
        double[] neww = new double[npts];
        for (i=0; i<npts; i++) { neww[i] = box[i].getWeight(); }
        for (iter=0; iter<1; iter++) {
            cx = 0;
            cy = 0;
            csum = 0;
            for (i=0; i<npts; i++) {
                cx = cx + neww[i]*box[i].getX();
                cy = cy + neww[i]*box[i].getY();
                csum = csum + neww[i];
            }
            if (csum==0) { return defout; }
            cx = cx/csum;
            cy = cy/csum;
            for (i=0; i<npts; i++) {
                dx = cx - box[i].getX();
                dy = cy - box[i].getY();
                dt = dx*dx+dy*dy;
                neww[i] = box[i].getWeight()*Math.exp(-dt/sig22);
            }
        }
        cx = 0;
        cy = 0;
        csum = 0;
        cmax = 0;
        for (i=0; i<npts; i++) {
            cx = cx + neww[i]*box[i].getX();
            cy = cy + neww[i]*box[i].getY();
            cmax = cmax + neww[i]*box[i].getWeight();
            csum = csum + neww[i];
        }
        if (csum==0) { return defout; }
        cx = cx/csum;
        cy = cy/csum;
        cmax = cmax/csum;
        
        WeightedPoint output = new WeightedPoint(cx, cy, cmax);
        return output;
    }
}
