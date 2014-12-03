#-----------------------------------------------------------
# appcompatcache_tln.pl
#
# History:
#  20130509 - added additional alert/warn checks
#  20130425 - added alertMsg() functionality
#  20120817 - updated to address extra data in XP data blocks
#  20120722 - updated %config hash
#  20120523 - created; updated from appcompatcache.pl
#  20120523 - updated to send all files to a single hash, and check for temp paths
#  20120515 - Updated to support 64-bit Win2003 and Vista/Win2008
#  20120424 - Modified/updated 
#  20120418 - created
#
# References:
#  Blog post: https://blog.mandiant.com/archives/2459
#  Whitepaper: http://fred.mandiant.com/Whitepaper_ShimCacheParser.pdf
#  Tool: https://github.com/mandiant/ShimCacheParser
#
# This plugin is based solely on the work and examples provided by Mandiant;
# thanks to them for sharing this information, and making the plugin possible.
# 
# copyright 2012 Quantum Analytics Research, LLC
# Author: H. Carvey, keydet89@yahoo.com
#-----------------------------------------------------------
package appcompatcache_tln;
use strict;

my %config = (hive          => "System",
							hivemask      => 4,
							output        => "tln",
							category      => "Program Execution",
              hasShortDescr => 1,
              hasDescr      => 0,
              hasRefs       => 0,
              osmask        => 31,  #XP - Win7
              version       => 20130509);

sub getConfig{return %config}
sub getShortDescr {
	return "Parse files from System hive Shim Cache";	
}
sub getDescr{}
sub getRefs {}
sub getHive {return $config{hive};}
sub getVersion {return $config{version};}

my $VERSION = getVersion();
my %files;

sub pluginmain {
	my $class = shift;
	my $hive = shift;
	::logMsg("Launching appcompatcache_tln v.".$VERSION);
	my $reg = Parse::Win32Registry->new($hive);
	my $root_key = $reg->get_root_key;
# First thing to do is get the ControlSet00x marked current...this is
# going to be used over and over again in plugins that access the system
# file
	my ($current,$ccs);
	my $key_path = 'Select';
	my $key;
	if ($key = $root_key->get_subkey($key_path)) {
		$current = $key->get_value("Current")->get_data();
		$ccs = "ControlSet00".$current;
		my $appcompat_path = $ccs."\\Control\\Session Manager";
		my $appcompat;
		if ($appcompat = $root_key->get_subkey($appcompat_path)) {
			
			my $app_data;
			
			eval {
				$app_data = $appcompat->get_subkey("AppCompatibility")->get_value("AppCompatCache")->get_data();
			};
			
			eval {
				$app_data = $appcompat->get_subkey("AppCompatCache")->get_value("AppCompatCache")->get_data();
			};
				
#			::rptMsg("Length of data: ".length($app_data));
			my $sig = unpack("V",substr($app_data,0,4));
#			::rptMsg(sprintf "Signature: 0x%x",$sig);
			
			if ($sig == 0xdeadbeef) {
				eval {
					appXP32Bit($app_data);
				};
			}
			elsif ($sig == 0xbadc0ffe) {
				eval {
					appWin2k3($app_data);
				};
			}
			elsif ($sig == 0xbadc0fee) {
				eval {
					appWin7($app_data);
				};
			
			}
			else {
				::rptMsg("Unknown signature");
			}
# this is where we print out the files
			foreach my $f (keys %files) {
				my $str;
				if (exists $files{$f}{executed}) {
					$str = "M... [Program Execution] AppCompatCache - ".$f;
				}
				else {
					$str = "M... AppCompatCache - ".$f;
				}
				$str .= " [Size = ".$files{$f}{size}." bytes]" if (exists $files{$f}{size});
#				$str .= " [Executed]" if (exists $files{$f}{executed}); 
				::rptMsg($files{$f}{modtime}."|REG|||".$str);

# added 20130603				
				alertCheckPathTLN($f,$files{$f}{modtime});
				alertCheckADSTLN($f,$files{$f}{modtime});
				::alertMsg($files{$f}{modtime}."|WARN|||Use of calcs\.exe. appcompatcache_tln: ".$f) if ($f =~ m/cacls\.exe$/);
			}
		}
		else {
			::rptMsg($appcompat_path." not found.");
		}
	}
	else {
		::rptMsg($key_path." not found.");
	}
}

#-----------------------------------------------------------
# appXP32Bit()
# parse 32-bit XP data
#-----------------------------------------------------------
sub appXP32Bit {
	my $data = shift;
	::rptMsg("WinXP, 32-bit");
# header is 400 bytes; each structure is 552 bytes in size
	my $num_entries = unpack("V",substr($data,4,4));
	
	foreach my $i (0..($num_entries - 1)) {
		my $x = substr($data,(400 + ($i * 552)),552);
		my $file = (split(/\00\00/,substr($x,0,488)))[0];
		$file =~ s/\00//g;
		$file =~ s/^\\\?\?\\//;
		my ($mod1,$mod2) = unpack("VV",substr($x,528,8));
		my $modtime      = ::getTime($mod1,$mod2);
		my ($sz1,$sz2)   = unpack("VV",substr($x,536,8));
		my $sz;
		($sz2 == 0)?($sz = $sz1):($sz = "Too big");
		my ($up1,$up2)   = unpack("VV",substr($x,544,8));
		my $updtime      = ::getTime($up1,$up2);
		
#		::rptMsg($file);
#		::rptMsg("Size   : ".$sz." bytes");
#		::rptMsg("ModTime: ".gmtime($modtime)." Z");
#		::rptMsg("UpdTime: ".gmtime($updtime)." Z");
#		::rptMsg("");
		$files{$file}{size} = $sz;
		$files{$file}{modtime} = $modtime;
		$files{$file}{updtime} = $updtime;
	}
}
#-----------------------------------------------------------
# appWin2k3()
# parse Win2k3, Vista, Win2k8 data
#-----------------------------------------------------------
sub appWin2k3 {
	my $data = shift;
	my $num_entries = unpack("V",substr($data,4,4));
#	::rptMsg("Num_entries: ".$num_entries);
	my $struct_sz = 0;
	my ($len,$max_len,$padding) = unpack("vvV",substr($data,8,8));
	if (($max_len - $len) == 2) {
# if $padding == 0, 64-bit; otherwise, 32-bit
		if ($padding == 0) {
			$struct_sz = 32;
			::rptMsg("Win2K3/Vista/Win2K8, 64-bit");
		}
		else {
			$struct_sz = 24;
			::rptMsg("Win2K3/Vista/Win2K8, 32-bit");
		}
	}
	
	foreach my $i (0..($num_entries - 1)) {
		my $struct = substr($data,(8 + ($struct_sz * $i)),$struct_sz);
		if ($struct_sz == 24) {
			my ($len,$max_len,$ofs,$t0,$t1,$f0,$f1) = unpack("vvVVVVV",$struct);
			
			my $file = substr($data,$ofs,$len);
			$file =~ s/\00//g;
			$file =~ s/^\\\?\?\\//;
			my $t = ::getTime($t0,$t1);
#			::rptMsg($file);
#			::rptMsg("  LastMod: ".gmtime($t)." Z");
#			::rptMsg("  [Executed]") if (($f0 < 4) && ($f0 & 0x2));
#			::rptMsg("");
			$files{$file}{modtime} = $t;
			$files{$file}{executed} = 1 if (($f0 < 4) && ($f0 & 0x2));
		}
		elsif ($struct_sz == 32) {
			my ($len,$max_len,$padding,$ofs0,$ofs1,$t0,$t1,$f0,$f1) = unpack("vvVVVVVVV",$struct);
			my $file = substr($data,$ofs0,$len);
			$file =~ s/\00//g;
			$file =~ s/^\\\?\?\\//;
			my $t = ::getTime($t0,$t1);
#			::rptMsg($file);
#			::rptMsg("  LastMod: ".gmtime($t)." Z");
#			::rptMsg("  Size   : ".$f0) if (($f1 == 0) && ($f0 > 3));
#			::rptMsg("  [Executed]") if (($f0 < 4) && ($f0 & 0x2));
#			::rptMsg("");
			$files{$file}{modtime} = $t;
			$files{$file}{size} = $f0 if (($f1 == 0) && ($f0 > 3));
			$files{$file}{executed} = 1 if (($f0 < 4) && ($f0 & 0x2));
		}
		else {
 			
			
		}
	}
}

#-----------------------------------------------------------
# appWin7()
# parse Win2k8R2, Win7 data
#-----------------------------------------------------------
sub appWin7 {
	my $data = shift;
	my $struct_sz = 0;
	my $num_entries = unpack("V",substr($data,4,4));
#	::rptMsg("Num_entries: ".$num_entries);
# 128-byte header	
	my ($len,$max_len,$padding) = unpack("vvV",substr($data,128,8));
	if (($max_len - $len) == 2) {
		if ($padding == 0) {
			$struct_sz = 48;
			::rptMsg("Win2K8R2/Win7, 64-bit");
		}
		else {
			$struct_sz = 32;
			::rptMsg("Win2K8R2/Win7, 32-bit");
		}
	}

	foreach my $i (0..($num_entries - 1)) {
		my $struct = substr($data,(128 + ($struct_sz * $i)),$struct_sz);
		if ($struct_sz == 32) {
			my ($len,$max_len,$ofs,$t0,$t1,$f0,$f1) = unpack("vvV5x8",$struct);
			my $file = substr($data,$ofs,$len);
			$file =~ s/\00//g;
			$file =~ s/^\\\?\?\\//;
			my $t = ::getTime($t0,$t1);
#			::rptMsg($file);
#			::rptMsg("  LastModTime: ".gmtime($t)." Z");
#			::rptMsg("  [Executed]") if ($f0 & 0x2);
#			::rptMsg("");		
			$files{$file}{modtime} = $t;
			$files{$file}{executed} = 1 if ($f0 & 0x2);
		}
		else {
			my ($len,$max_len,$padding,$ofs0,$ofs1,$t0,$t1,$f0,$f1) = unpack("vvV7x16",$struct);
			my $file = substr($data,$ofs0,$len);
			$file =~ s/\00//g;
			$file =~ s/^\\\?\?\\//;
			my $t = ::getTime($t0,$t1);
#			::rptMsg($file);
#			::rptMsg("  LastModTime: ".gmtime($t)." Z");
#			::rptMsg("  [Executed]") if ($f0 & 0x2);
#			::rptMsg("");		
			$files{$file}{modtime} = $t;
			$files{$file}{executed} = 1 if ($f0 & 0x2);
		}
	}
}

#-----------------------------------------------------------
# alertCheckPath()
#-----------------------------------------------------------
sub alertCheckPathTLN {
	my $path = shift;
	my $tln  = shift;
	$path = lc($path);
	my @alerts = ("recycle","globalroot","temp","system volume information","appdata",
	              "application data");
	
	foreach my $a (@alerts) {
		if (grep(/$a/,$path)) {
			::alertMsg($tln."|ALERT|||appcompatcache_tln: ".$a." found in path: ".$path);              
		}
	}
}

#-----------------------------------------------------------
# alertCheckADS()
#-----------------------------------------------------------
sub alertCheckADSTLN {
	my $path = shift;
	my $tln  = shift;
	my @list = split(/\\/,$path);
	my $last = $list[scalar(@list) - 1];
	::alertMsg($tln."|ALERT|||appcompatcache_tln: Poss. ADS found in path: ".$path) if grep(/:/,$last);
}
1;
