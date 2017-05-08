#! c:\perl\bin\perl.exe
#-----------------------------------------------------------
# ie_settings.pl
# Gets IE settings
#
# Change history
#  20130731 - added check for "ClearBrowsingHistoryOnExit"
#  20130328 - added "AutoConfigURL" value info
#  20130223 - updated
#  20091016 - created
#
# References
#   http://blog.digital-forensics.it/2012/05/exploring-internet-explorer-with.html
# 
# 
# copyright 2013 QAR, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package ie_settings;
use strict;

my %config = (hive          => "NTUSER\.DAT",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 1,
              osmask        => 22,
              version       => 20130731);

sub getConfig{return %config}
sub getShortDescr {
	return "Gets important user IE settings";	
}
sub getDescr{}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();

sub pluginmain {
	my $class = shift;
	my $ntuser = shift;
	::logMsg("Launching ie_settings v.".$VERSION);
	::rptMsg("ie_settings v.".$VERSION); # banner
    ::rptMsg("(".getHive().") ".getShortDescr()."\n"); # banner
	my $reg = Parse::Win32Registry->new($ntuser);
	my $root_key = $reg->get_root_key;
	
	my $key_path = 'Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		::rptMsg("");
		my $ua;
		eval {
			$ua = $key->get_value("User Agent")->get_data();
			::rptMsg("User Agent = ".$ua);
		};
		
		my $zonessecupgrade;
		eval {
			$zonessecupgrade = $key->get_value("ZonesSecurityUpgrade")->get_data();
			my ($z0,$z1) = unpack("VV",$zonessecupgrade);
			::rptMsg("ZonesSecurityUpgrade = ".gmtime(::getTime($z0,$z1))." (UTC)");
		};
		
		my $daystokeep;
		eval {
			$daystokeep = $key->get_subkey("Url History")->get_value("DaysToKeep")->get_data();
			::rptMsg("DaysToKeep = ".$daystokeep);
		};
		if ($@) {
			::rptMsg("DaysToKeep value not found - default is 20 days");
		}
# added check for "delete history on exit" setting 20130731
		my $clear;
		eval {
			$clear = $key->get_subkey("Privacy")->get_value("ClearBrowsingHistoryOnExit")->get_data();
			::rptMsg("ClearBrowsingHistoryOnExit = ".$clear);
# 1 = enabled			
		};		

# AutoConfigURL
# ref: http://technet.microsoft.com/en-us/library/cc736412%28v=ws.10%29.aspx
#      http://blog.spiderlabs.com/2012/04/brazilian-banking-malware-pay-your-bill-slacker-.html		
		eval {
			my $auto = $key->get_value("AutoConfigURL")->get_data();
			::rptMsg("AutoConfigURL: ".$auto);
			::rptMsg("**Possible malware indicator found!!");
		};
		
	}
	else {
		::rptMsg($key_path." not found.");
	}
#-----------------------------------------------------------	
# Windows Search integration into IE	
# Windows Search indexes URLs for autocompletion
#
# Ref:
#  http://www.ghacks.net/2011/03/17/disable-indexing-of-internet-explorer-web-history-by-windows-search/
#
#
#-----------------------------------------------------------
	$key_path = 'Software\\Microsoft\\Internet Explorer\\Main\\WindowsSearch';
	if ($key = $root_key->get_subkey($key_path)) {
		::rptMsg("");
		::rptMsg($key_path);
		::rptMsg("LastWrite Time ".gmtime($key->get_timestamp())." (UTC)");
		
		eval {
			my $v = $key->get_value("Version")->get_data();
			::rptMsg("Version = ".$v);
		};
		
		::rptMsg("");
# Gets information about when the IE history was last cleared by the user		
		my $cleared;
		eval {
			$cleared = $key->get_value("Cleared")->get_data();
			if ($cleared == 1) {
				::rptMsg("Cleared = 1");
				my @t = unpack("VV",$key->get_value("Cleared_TIMESTAMP")->get_data());
				my $cl_ts = ::getTime($t[0],$t[1]);
				::rptMsg("Cleared_TIMESTAMP = ".gmtime($cl_ts)." UTC");
				::rptMsg("Analysis Tip: The \'Cleared\' value indicates that the user account ");
				::rptMsg("was used to clear the IE browser history, and the timestamp value indicates");
				::rptMsg("when this occurred\.");
			}
		};
		if ($@) {
			::rptMsg("\'Cleared\' value not found\.");
		}
		::rptMsg("");
		eval {
			my @v = unpack("VV",$key->get_value("LastCrawl")->get_data());
			my $crawl = ::getTime($v[0],$v[1]);
			::rptMsg("LastCrawl = ".gmtime($crawl)." UTC");
		};
		
		eval {
			my @v = unpack("VV",$key->get_value("UpgradeTime")->get_data());
			my $up = ::getTime($v[0],$v[1]);
			::rptMsg("UpgradeTime = ".gmtime($up)." UTC");
		};
		
		eval {
			my $path = $key->get_value("User Favorites Path")->get_data();
			::rptMsg("User Favorites Path = ".$path);
		};
	
	}
}
1;
