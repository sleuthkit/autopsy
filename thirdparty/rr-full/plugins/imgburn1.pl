#-----------------------------------------------------------
# imgburn1.pl
# 
# Gets user's ImgBurn recent files and configured paths 
#
# History
#   20180630 - created
#
# References
#	http://forum.imgburn.com/index.php?/forum/4-guides/
#
# 
# copyright 2018 Michael Godfrey mgodfrey [at] gmail.com
#-----------------------------------------------------------
package imgburn1;
use strict;


my %config =
(
  hive          => "NTUSER\.DAT",
  hasShortDescr => 0,
  hasDescr      => 1,
  hasRefs       => 1,
  osmask        => 29,
  version       => 20180630
);

sub getConfig     {return %config;}
sub getDescr      {return "Gets user's ImgBurn MRU files and paths from NTUSER";}
sub getRefs       {return "n/a";}
sub getHive       {return $config{hive};}
sub getVersion    {return $config{version};}

my $VERSION = getVersion();


sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching imgburn1 v.".$VERSION);
    ::rptMsg('imgburn1 v'.$VERSION.' ('.getDescr().")");
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
	my $key_path = 'Software\\ImgBurn';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		my $id;
		eval {
			$id = $key->get_value("InstallDirectory")->get_data();
			
		};
		if ($@) {
			::rptMsg("InstallDirectory value not found.");
		}
		else {
			::rptMsg("InstallDirectory = ".$id);
		}
		

		my $bq;
		eval {
			$bq = $key->get_value("IBQ_MRUFile")->get_data();
			
		};
		if ($@) {
			::rptMsg("IBQ_MRUFile value not found.");
		}
		else {
			::rptMsg("IBQ_MRUFile = ".$bq);
		}	
	

		my $rf;
		eval {
			$rf = $key->get_value("ISOREAD_RecentFiles_Destination")->get_data();
			
		};
		if ($@) {
			::rptMsg("ISOREAD_RecentFiles_Destination value not found.");
		}
		else {
			::rptMsg("ISOREAD_RecentFiles_Destination = ".$rf);
		}
		
		
		my $rs;
		eval {
			$rs = $key->get_value("ISOWRITE_RecentFiles_Source")->get_data();
			
		};
		if ($@) {
			::rptMsg("ISOWRITE_RecentFiles_Source value not found.");
		}
		else {
			::rptMsg("ISOWRITE_RecentFiles_Source = ".$rs);
		}
		
		
		my $sf;
		eval {
			$sf = $key->get_value("ISOBUILD_MRUSourceFolder")->get_data();
			
		};
		if ($@) {
			::rptMsg("ISOBUILD_MRUSourceFolder value not found.");
		}
		else {
			::rptMsg("ISOBUILD_MRUSourceFolder = ".$sf);
		}
				
		
		my $fs;
		eval {
			$fs = $key->get_value("ISOBUILD_RecentFiles_Source")->get_data();
			
		};
		if ($@) {
			::rptMsg("ISOBUILD_RecentFiles_Source value not found.");
		}
		else {
			::rptMsg("ISOBUILD_RecentFiles_Source = ".$fs);
		}		
		
		
		my $fd;
		eval {
			$fd = $key->get_value("ISOBUILD_RecentFiles_Destination")->get_data();
			
		};
		if ($@) {
			::rptMsg("ISOBUILD_RecentFiles_Destination value not found.");
		}
		else {
			::rptMsg("ISOBUILD_RecentFiles_Destination = ".$fd);
		}
		
		
		my $fd;
		eval {
			$fd = $key->get_value("ISOBUILD_Recentfolders_Destination")->get_data();
			
		};
		if ($@) {
			::rptMsg("ISOBUILD_RecentFolders_Destination value not found.");
		}
		else {
			::rptMsg("ISOBUILD_RecentFolders_Destination = ".$fd);
		}
		
		
		my $if;
		eval {
			$if = $key->get_value("FILELOCATIONS_ImageFiles")->get_data();
			
		};
		if ($@) {
			::rptMsg("FILELOCATIONS_ImageFiles value not found.");
		}
		else {
			::rptMsg("FILELOCATIONS_ImageFiles = ".$if);
		}
	
		my $lf;
		eval {
			$lf = $key->get_value("FILELOCATIONS_LogFiles")->get_data();
			
		};
		if ($@) {
			::rptMsg("FILELOCATIONS_LogFiles value not found.");
		}
		else {
			::rptMsg("FILELOCATIONS_LogFiles = ".$lf);
		}
	
		
		my $pf;
		eval {
			$pf = $key->get_value("FILELOCATIONS_ProjectFiles")->get_data();
			
		};
		if ($@) {
			::rptMsg("FILELOCATIONS_ProjectFiles value not found.");
		}
		else {
			::rptMsg("FILELOCATIONS_ProjectFiles = ".$pf);
		}
	
	
		my $qf;
		eval {
			$qf = $key->get_value("FILELOCATIONS_QueueFiles")->get_data();
			
		};
		if ($@) {
			::rptMsg("FILELOCATIONS_QueueFiles value not found.");
		}
		else {
			::rptMsg("FILELOCATIONS_QueueFiles = ".$qf);
		}
	
	
	}
	else {
		::rptMsg($key_path." not found.");
	}
}
1;