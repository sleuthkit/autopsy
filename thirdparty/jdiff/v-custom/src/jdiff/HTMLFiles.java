package jdiff;

import java.util.*;
import java.io.*;

/**
 * Emit HTML files for the supporting infrastructure for the HTML report.
 * Examples are stylesheets, help files, frame files.
 *
 * See the file LICENSE.txt for copyright details.
 * @author Matthew Doar, mdoar@pobox.com
 */
public class HTMLFiles {

    /** Constructor. */
    public HTMLFiles(HTMLReportGenerator h) {
        h_ = h;
    }   

    /** The HTMLReportGenerator instance used to write HTML. */
    private HTMLReportGenerator h_ = null;

    /** 
     * Emit the top-level changes.html frames file where everything starts.
     */
    public void emitTopLevelFile(String tln,
                                 APIDiff apiDiff) {
        try {
            FileOutputStream fos = new FileOutputStream(tln);
            h_.reportFile = new PrintWriter(fos);
            // Write out the HTML header
            h_.writeStartHTMLHeaderWithDate();
            // Write out the title
            String oldAPIName = "Old API";
            if (apiDiff.oldAPIName_ != null)
                oldAPIName = apiDiff.oldAPIName_;
            String newAPIName = "New API";
            if (apiDiff.newAPIName_ != null)
                newAPIName = apiDiff.newAPIName_;
            if (h_.windowTitle == null) 
                h_.writeHTMLTitle("API Differences between " + oldAPIName + " and " + newAPIName);
            else
                h_.writeHTMLTitle(h_.windowTitle);
            // Note that the stylesheet is in the same directory
            h_.writeStyleSheetRef(true);
            h_.writeText("</HEAD>");
            // Note that the top-level frame file doesn't have the BODY tag
            h_.writeText("<FRAMESET COLS=\"20%,80%\">");
            h_.writeText("  <FRAMESET ROWS=\"25%,75%\">");

            // Convert filenames to web links
            String tlfLink = h_.reportFileName + "/jdiff_topleftframe" + h_.reportFileExt;
            String allDiffsLink = h_.reportFileName + "/alldiffs_index_all" + h_.reportFileExt;
            String csnLink = h_.reportFileName + "/" + h_.reportFileName + "-summary" + h_.reportFileExt;

            h_.writeText("    <FRAME SRC=\"" + tlfLink + "\" SCROLLING=\"no\" NAME=\"topleftframe\">");
            h_.writeText("    <FRAME SRC=\"" + allDiffsLink + "\" SCROLLING=\"auto\" NAME=\"bottomleftframe\">");
            h_.writeText("  </FRAMESET>");
            h_.writeText("  <FRAME SRC=\"" + csnLink + "\" SCROLLING=\"auto\" NAME=\"rightframe\">");
            h_.writeText("</FRAMESET>");
            h_.writeText("<NOFRAMES>");
            h_.writeText("<H2>");
            h_.writeText("Frame Alert");
            h_.writeText("</H2>\n");
            h_.writeText("<P>");
            h_.writeText("This document is designed to be viewed using the frames feature. If you see this message, you are using a non-frame-capable web client.");
            h_.writeText("<BR>");
            h_.writeText("Link to <A HREF=\"" + csnLink + "\" target=\"_top\">Non-frame version.</A>");
            h_.writeText("</NOFRAMES>");
            h_.writeText("</HTML>");
            h_.reportFile.close();
        } catch(IOException e) {
            System.out.println("IO Error while attempting to create " + tln);
            System.out.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    /** Emit a top left frame with all the links to the index files. */
    public void emitTopLeftFile(String tlf) {
        try {
            FileOutputStream fos = new FileOutputStream(tlf);
            h_.reportFile = new PrintWriter(fos);
            h_.writeStartHTMLHeader();
            h_.writeHTMLTitle("JDiff");
            h_.writeStyleSheetRef();
            h_.writeText("</HEAD>");
            h_.writeText("<BODY>");

            h_.writeText("<TABLE summary=\"Links to all index files\" BORDER=\"0\" WIDTH=\"100%\" cellspacing=\"0\" cellpadding=\"0\">");
            h_.writeText("<TR>");
            h_.writeText("  <TD NOWRAP bgcolor=\"#FFFFCC\"><FONT size=\"+1\">");
            h_.writeText("  <B>JDiff&nbsp;Indexes</B></FONT><br></TD>");
            h_.writeText("</TR>");
            h_.writeText("<TR>");
            h_.writeText("  <TD NOWRAP bgcolor=\"#FFFFFF\"><FONT CLASS=\"FrameItemFont\"><A HREF=\"alldiffs_index_all" + h_.reportFileExt + "\" TARGET=\"bottomleftframe\">All Differences</A></FONT><br></TD>");
            h_.writeText("</TR>");
            h_.writeText("<TR>");
            h_.writeText("  <TD NOWRAP bgcolor=\"#FFFFFF\"><FONT CLASS=\"FrameItemFont\"><A HREF=\"packages_index_all" + h_.reportFileExt + "\" TARGET=\"bottomleftframe\">By Package</A></FONT><br></TD>");
            h_.writeText("</TR>");
            h_.writeText("<TR>");
            h_.writeText("  <TD NOWRAP bgcolor=\"#FFFFFF\"><FONT CLASS=\"FrameItemFont\"><A HREF=\"classes_index_all" + h_.reportFileExt + "\" TARGET=\"bottomleftframe\">By Class</A></FONT><br></TD>");
            h_.writeText("</TR>");
            h_.writeText("<TR>");
            h_.writeText("  <TD NOWRAP bgcolor=\"#FFFFFF\"><FONT CLASS=\"FrameItemFont\"><A HREF=\"constructors_index_all" + h_.reportFileExt + "\" TARGET=\"bottomleftframe\">By Constructor</A></FONT><br></TD>");
            h_.writeText("</TR>");
            h_.writeText("<TR>");
            h_.writeText("  <TD NOWRAP bgcolor=\"#FFFFFF\"><FONT CLASS=\"FrameItemFont\"><A HREF=\"methods_index_all" + h_.reportFileExt + "\" TARGET=\"bottomleftframe\">By Method</A></FONT><br></TD>");
            h_.writeText("</TR>");
            h_.writeText("<TR>");
            h_.writeText("  <TD NOWRAP bgcolor=\"#FFFFFF\"><FONT CLASS=\"FrameItemFont\"><A HREF=\"fields_index_all" + h_.reportFileExt + "\" TARGET=\"bottomleftframe\">By Field</A></FONT><br></TD>");
            h_.writeText("</TR>");
            h_.writeText("</TABLE>");

            h_.writeHTMLFooter();
            h_.reportFile.close();
        } catch(IOException e) {
            System.out.println("IO Error while attempting to create " + tlf);
            System.out.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    /** Emit the help file. */
    public void emitHelp(String fullReportFileName, APIDiff apiDiff) {
        String helpFileName = fullReportFileName + JDiff.DIR_SEP + "jdiff_help" + h_.reportFileExt;
        try {
            FileOutputStream fos = new FileOutputStream(helpFileName);
            h_.reportFile = new PrintWriter(fos);
            h_.writeStartHTMLHeader();
            h_.writeHTMLTitle("JDiff Help");
            h_.writeStyleSheetRef();
            h_.writeText("</HEAD>");
            h_.writeText("<BODY>");
            // Write a customized navigation bar for the help page
            h_.writeText("<!-- Start of nav bar -->");
            h_.writeText("<TABLE summary=\"Navigation bar\" BORDER=\"0\" WIDTH=\"100%\" CELLPADDING=\"1\" CELLSPACING=\"0\">");
            h_.writeText("<TR>");
            h_.writeText("<TD COLSPAN=2 BGCOLOR=\"#EEEEFF\" CLASS=\"NavBarCell1\">");
            h_.writeText("  <TABLE summary=\"Navigation bar\" BORDER=\"0\" CELLPADDING=\"0\" CELLSPACING=\"3\">");
            h_.writeText("    <TR ALIGN=\"center\" VALIGN=\"top\">");
            // Always have a link to the Javadoc files
            h_.writeText("      <TD BGCOLOR=\"#EEEEFF\" CLASS=\"NavBarCell1\"> <A HREF=\"" + h_.newDocPrefix + "index.html\" target=\"_top\"><FONT CLASS=\"NavBarFont1\"><B><tt>" + apiDiff.newAPIName_ + "</tt></B></FONT></A>&nbsp;</TD>");
            h_.writeText("      <TD BGCOLOR=\"#EEEEFF\" CLASS=\"NavBarCell1\"> <A HREF=\"" + h_.reportFileName + "-summary" + h_.reportFileExt + "\"><FONT CLASS=\"NavBarFont1\"><B>Overview</B></FONT></A>&nbsp;</TD>");
            h_.writeText("      <TD BGCOLOR=\"#EEEEFF\" CLASS=\"NavBarCell1\"> &nbsp;<FONT CLASS=\"NavBarFont1\">Package</FONT>&nbsp;</TD>");
            h_.writeText("      <TD BGCOLOR=\"#FFFFFF\" CLASS=\"NavBarCell1\"> &nbsp;<FONT CLASS=\"NavBarFont1\">Class</FONT>&nbsp;</TD>");
            if (!Diff.noDocDiffs) {
                h_.writeText("      <TD BGCOLOR=\"#EEEEFF\" CLASS=\"NavBarCell1\"> <A HREF=\"" + Diff.diffFileName + "index" + h_.reportFileExt + "\"><FONT CLASS=\"NavBarFont1\"><B>Text Changes</B></FONT></A>&nbsp;</TD>");
            }
            if (h_.doStats) {
                h_.writeText("      <TD BGCOLOR=\"#EEEEFF\" CLASS=\"NavBarCell1\"> <A HREF=\"jdiff_statistics" + h_.reportFileExt + "\"><FONT CLASS=\"NavBarFont1\"><B>Statistics</B></FONT></A>&nbsp;</TD>");
            }
            h_.writeText("      <TD BGCOLOR=\"#EEEEFF\" CLASS=\"NavBarCell1Rev\"> &nbsp;<FONT CLASS=\"NavBarFont1Rev\"><B>Help</B></FONT>&nbsp;</TD>");
            h_.writeText("    </TR>");
            h_.writeText("  </TABLE>");
            h_.writeText("</TD>");

            // The right hand side title
            h_.writeText("<TD ALIGN=\"right\" VALIGN=\"top\" ROWSPAN=3><EM><b>Generated by<br><a href=\"" + JDiff.jDiffLocation + "\" class=\"staysblack\" target=\"_top\">JDiff</a></b></EM></TD>");
            h_.writeText("</TR>");
            
            // Links for frames and no frames
            h_.writeText("<TR>");
            h_.writeText("  <TD BGCOLOR=\"" + h_.bgcolor + "\" CLASS=\"NavBarCell2\"><FONT SIZE=\"-2\"></FONT>");
            h_.writeText("</TD>");
            h_.writeText("  <TD BGCOLOR=\"" + h_.bgcolor + "\" CLASS=\"NavBarCell2\"><FONT SIZE=\"-2\">");
            h_.writeText("  <A HREF=\"" + "../" + h_.reportFileName + h_.reportFileExt + "\" TARGET=\"_top\"><B>FRAMES</B></A>  &nbsp;");
            h_.writeText("  &nbsp;<A HREF=\"jdiff_help" + h_.reportFileExt + "\" TARGET=\"_top\"><B>NO FRAMES</B></A></FONT></TD>");
            h_.writeText("</TR>");
            
            h_.writeText("</TABLE>");
            h_.writeText("<HR>");
            h_.writeText ("<!-- End of nav bar -->");

            h_.writeText("<center>");        
            h_.writeText("<H1>JDiff Documentation</H1>");
            h_.writeText("</center>");        

            h_.writeText("<BLOCKQUOTE>");
            h_.writeText("JDiff is a <a href=\"http://java.sun.com/j2se/javadoc/\" target=\"_top\">Javadoc</a> doclet which generates a report of the API differences between two versions of a product. It does not report changes in Javadoc comments, or changes in what a class or method does. ");
            h_.writeText("This help page describes the different parts of the output from JDiff.");
            h_.writeText("</BLOCKQUOTE>");

            h_.writeText("<BLOCKQUOTE>");
            h_.writeText(" See the reference page in the <a href=\"" + JDiff.jDiffLocation + "\">source for JDiff</a> for information about how to generate a report like this one.");
            h_.writeText("</BLOCKQUOTE>");

            h_.writeText("<BLOCKQUOTE>");
            h_.writeText("The indexes shown in the top-left frame help show each type of change in more detail. The index \"All Differences\" contains all the differences between the APIs, in alphabetical order. ");
            h_.writeText("These indexes all use the same format:");
            h_.writeText("<ul>");
            h_.writeText("<li>Removed packages, classes, constructors, methods and fields are <strike>struck through</strike>.</li>");
            h_.writeText("<li>Added packages, classes, constructors, methods and fields appear in <b>bold</b>.</li>");
            h_.writeText("<li>Changed packages, classes, constructors, methods and fields appear in normal text.</li>");
            h_.writeText("</ul>");
            h_.writeText("</BLOCKQUOTE>");

            h_.writeText("<BLOCKQUOTE>");
            h_.writeText("You can always tell when you are reading a JDiff page, rather than a Javadoc page, by the color of the index bar and the color of the background. ");
            h_.writeText("Links which take you to a Javadoc page are always in a <tt>typewriter</tt> font. ");
            h_.writeText("Just like Javadoc, all interface names are in <i>italic</i>, and class names are not italicized. Where there are multiple entries in an index with the same name, the heading for them is also in italics, but is not a link.");
            h_.writeText("</BLOCKQUOTE>");

            h_.writeText("<BLOCKQUOTE>");
            h_.writeText("<H3><b><tt>Javadoc</tt></b></H3>");
            h_.writeText("This is a link to the <a href=\"" + h_.newDocPrefix + "index.html\" target=\"_top\">top-level</a> Javadoc page for the new version of the product.");
            h_.writeText("</BLOCKQUOTE>");
            
            h_.writeText("<BLOCKQUOTE>");
            h_.writeText("<H3>Overview</H3>");        
            h_.writeText("The <a href=\"" + h_.reportFileName + "-summary" + 
                      h_.reportFileExt + "\">overview</a> is the top-level summary of what was removed, added and changed between versions.");        
            h_.writeText("</BLOCKQUOTE>");
            
            h_.writeText("<BLOCKQUOTE>");
            h_.writeText("<H3>Package</H3>");        
            h_.writeText("This is a link to the package containing the current changed class or interface.");        
            h_.writeText("</BLOCKQUOTE>");
            
            h_.writeText("<BLOCKQUOTE>");
            h_.writeText("<H3>Class</H3>");        
            h_.writeText("This is highlighted when you are looking at the changed class or interface.");        
            h_.writeText("</BLOCKQUOTE>");
            
            h_.writeText("<BLOCKQUOTE>");
            h_.writeText("<H3>Text Changes</H3>");        
            h_.writeText("This is a link to the top-level index of all documentation changes for the current package or class. ");        
            h_.writeText("If it is not present, then there are no documentation changes for the current package or class. ");        
            h_.writeText("This link can be removed entirely by not using the <code>-docchanges</code> option.");        
            h_.writeText("</BLOCKQUOTE>");
            
            h_.writeText("<BLOCKQUOTE>");
            h_.writeText("<H3>Statistics</H3>");        
            h_.writeText("This is a link to a page which shows statistics about the changes between the two APIs.");        
            h_.writeText("This link can be removed entirely by not using the <code>-stats</code> option.");        
            h_.writeText("</BLOCKQUOTE>");
            
            h_.writeText("<BLOCKQUOTE>");
            h_.writeText("<H3>Help</H3>");        
            h_.writeText("A link to this Help page for JDiff.");
            h_.writeText("</BLOCKQUOTE>");

            h_.writeText("<BLOCKQUOTE>");
            h_.writeText("<H3>Prev/Next</H3>");        
            h_.writeText("These links take you to the previous  and next changed package or class.");        
            h_.writeText("</BLOCKQUOTE>");
            
            h_.writeText("<BLOCKQUOTE>");
            h_.writeText("<H3>Frames/No Frames</H3>");        
            h_.writeText("These links show and hide the HTML frames. All pages are available with or without frames.");        
            h_.writeText("</BLOCKQUOTE>");
            
            h_.writeText("<BLOCKQUOTE>");
            h_.writeText("<H2>Complex Changes</H2>");
            h_.writeText("There are some complex changes which can occur between versions, for example, when two or more methods with the same name change simultaneously, or when a method or field is moved into or from a superclass. ");
            h_.writeText("In these cases, the change will be seen as a removal and an addition, rather than as a change. Unexpected removals or additions are often part of one of these type of changes. ");
            h_.writeText("</BLOCKQUOTE>");
            
            h_.writeHTMLFooter();
            h_.reportFile.close();
        } catch(IOException e) {
            System.out.println("IO Error while attempting to create " + helpFileName);
            System.out.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    /** Emit the CSS external stylesheet file. */
    public void emitStylesheet() {
        String stylesheetFileName = "stylesheet-jdiff.css";
        if (h_.outputDir != null)
            stylesheetFileName = h_.outputDir + JDiff.DIR_SEP +stylesheetFileName;
        try {
            FileOutputStream fos = new FileOutputStream(stylesheetFileName);
            h_.reportFile = new PrintWriter(fos);
            h_.writeText();
            h_.writeText("/* The JDiff style sheet, derived from the Javadoc style sheet. */");
            h_.writeText("/* Generated by the JDiff Javadoc doclet */");
            h_.writeText("/* (" + JDiff.jDiffLocation + ") */");
//            h_.writeText("/* on " + new Date() + " */");
            h_.writeText();
            h_.writeText("/* Define colors, fonts and other style attributes here to override the defaults  */");
            h_.writeText();
            h_.writeText("/* Page background color */");
//            h_.writeText("body { background-color: " + h_.bgcolor + "; font-family: arial; }");
            // First argument after backgroun: is for older Netscape browsers
            // For more information, see http://css.nu/pointers/bugs.html and 
            // http://www.richinstyle.com/bugs/netscape4.html
            h_.writeText("body { background: #CCFFFF url(background.gif); font-family: arial; }");
            h_.writeText();
            h_.writeText("/* Table colors */");
            h_.writeText(".TableHeadingColor     { background: #CCCCFF } /* Dark mauve */");
            h_.writeText(".TableSubHeadingColor  { background: #EEEEFF } /* Light mauve */");
            h_.writeText(".TableRowColor         { background: #FFFFFF } /* White */");
            h_.writeText();
            h_.writeText("/* Font used in left-hand frame lists */");
            h_.writeText(".FrameTitleFont   { font-size: normal; font-family: normal }");
            h_.writeText(".FrameHeadingFont { font-size: normal; font-family: normal }");
            h_.writeText(".FrameItemFont    { font-size: normal; font-family: normal }");
            h_.writeText();
            h_.writeText("/* Example of smaller, sans-serif font in frames */");
            h_.writeText("/* .FrameItemFont  { font-size: 10pt; font-family: Helvetica, Arial, sans-serif } */");
            h_.writeText();
            h_.writeText("/* Navigation bar fonts and colors */");
            h_.writeText(".NavBarCell1    { background-color:#FFFFCC;} /* Changed to yellowish to make difference from Javadoc clear */");
            h_.writeText(".NavBarCell1Rev { background-color:#00008B;}/* Dark Blue */");
            h_.writeText(".NavBarFont1    { font-family: Arial, Helvetica, sans-serif; color:#000000;}");
            h_.writeText(".NavBarFont1Rev { font-family: Arial, Helvetica, sans-serif; color:#FFFFFF;}");
            h_.writeText();
            h_.writeText(".NavBarCell2    { font-family: Arial, Helvetica, sans-serif; background-color:#FFFFFF;}");
            h_.writeText(".NavBarCell3    { font-family: Arial, Helvetica, sans-serif; background-color:#FFFFFF;}");
            h_.writeText();
            h_.writeText("/* ");
            h_.writeText(" Links which become blue when hovered upon and show that they have been ");
            h_.writeText(" visited. ");
            h_.writeText("*/");
            h_.writeText("a.hiddenlink:link      {color: black; text-decoration: none}");
            h_.writeText("a.hiddenlink:visited   {color: purple; text-decoration: none}");
            h_.writeText("a.hiddenlink:hover     {color: blue; text-decoration: underline;}");
            h_.writeText();
            h_.writeText("/* ");
            h_.writeText(" Links which become blue when hovered upon but do not show that they have ");
            h_.writeText(" been visited. ");
            h_.writeText("*/");
            h_.writeText("a.staysblack:link     {color: black; text-decoration: none}");
            h_.writeText("a.staysblack:visited  {color: black; text-decoration: none}");
            h_.writeText("a.staysblack:hover    {color: blue; text-decoration: underline;}");
            h_.reportFile.close();
        } catch(IOException e) {
            System.out.println("IO Error while attempting to create " + stylesheetFileName);
            System.out.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

}
