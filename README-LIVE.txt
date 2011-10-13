                     Autopsy Forensic Browser
                 http://www.sleuthkit.org/autopsy

                        Live Analysis Mode

                   Last Updated:  January 2005


What is Live Analysis?
--------------------------------------------------------------------
Live analysis is, in my mind, an investigation that occurs using
the software resources of the suspect system.  An example scenario
of this is when a suspect system is found running, a CD is placed
into it, and commands are run.  If the suspect system is powered
down and booted from a bootable Linux CD (or similar), then the
investigation is a dead analysis.

This is most commonly done when investigating a server or other
computer that is suspected of being compromised, but verification
is needed before it can be powered down.  Using The Sleuth Kit and
Autopsy will prevent the access times on individual files from being
updated (although the raw device's A-time will be) and can bypass
most rootkits that hide files and directories.


What are the Issues with Live Analysis?
--------------------------------------------------------------------
Live analysis is not ideal because you are relying on the suspect
system, which can lie, cheat, and steal.  In addition to the potential
of getting false information from the operating system you will
also overwrite memory and maybe swap space during the investigation.

If you are interested in examining the memory of the system, you
should probably acquire that before you begin a live analysis.

An issue with doing live analysis with Autopsy is that it requires
Perl, which is a large program and will likely need to depend on
libraries and other files on the suspect system.


How do I make a CD with Autopsy on it?
--------------------------------------------------------------------

You will want to have a trusted CD for a live analysis, and autopsy
makes that fairly easy.  Compile autopsy as you would for a normal
dead analysis installation.  Then execute 'make live' in Autopsy.
This script will make a 'live-cd' sub-directory in the autopsy directory,
which contains a copy of autopsy and copies of TSK executables, grep,
strings, perl etc:

    # make live
    Making base directory (./live-cd/)
    Copying executables
    Copying autopsy files
    Creating configuration file using existing settings

Try the 'make static' with TSK to see if you can make static
executables for your platform.  

The 'live-cd' directory has a 'bin' directory where additional
executables can be copied to and then the whole directory can be
burned to a CD.


How Do I Use the CD?
--------------------------------------------------------------------

After the CD has been created and there is a system suspected of
being compromised, then it is time to take advantage of the new
features.  There are two scenarios for live analysis.  The first
scenario uses a network share from a trusted system that you can
write to.  In this case, autopsy is run as normal and you specify
the evidence locker directory as the mounted disk.  The evidence
locker is specified with '-d':

    # ./autopsy -d /mnt/ev_lock 10.1.32.123

The above would start autopsy, use '/mnt/ev_lock/' as the evidence
locker and would allow connections from 10.1.32.123 (where the
investigator would connect from using an HTML browser).  Remember that
we do not want to write to the suspect system, so we should only use
a network share and not a local directory in this scenario.

The second scenario does not use an evidence locker and does not
intentionally write any data to disk.  This scenario does not need
the network share and each of the devices (or partitions) that will
be analyzed are specified on the command line using the '-i' flags.
The '-i' flag requires three arguments: the device, the file system
type, and the mounting point.  For example, to examine the '/dev/hda5'
and '/dev/hda8' partitions on a Linux system, the following could
be used:

    # ./autopsy -i /dev/hda5 linux-ext3 / -i /dev/hda8 linux-ext3 /usr/ \
    10.1.32.123

The file system type must be one of the types that are supported
by TSK.  The remote IP address must also be given, otherwise you
will have to use a browser on the suspect system and that will write
data to the disk.

When you use the '-i' flag, then autopsy will start in the 'Host
Manager' view where you can select the image that you want to
analyze.  You will skip the case and host configuration.  The default
case name will be 'live', the default host name is 'local', and the
default investigator name is 'unknown'.


Additional Information
--------------------------------------------------------------------
I wrote a more detailed explanation of the live analysis mode of
Autopsy version 2.00 in the 13th issue of The Sleuth Kit Informer.
Some of this document is taken from the Informer issue.

    http://www.sleuthkit.org/informer/sleuthkit-informer-13.html


--------------------------------------------------------------------
Copyright (c) 2004 by Brian Carrier.  All Rights Reserved
Brian Carrier [carrier <at> sleuthkit <dot> org]
