#! c:\perl\bin\perl.exe
#-----------------------------------------------------------
# Plugins Browser - browse plugins, create plugins files, edit
#                   current files
#
#
# Change History
#   20100122 - Updated to include opening a plugins file
#   20091207 - Created
#
# copyright 2010 Quantum Analytics Research, LLC
#-----------------------------------------------------------
use strict;
use Win32::GUI();
use Win32::GUI::Constants qw(CW_USEDEFAULT);
use Encode;

my $plugindir;

my $mw = Win32::GUI::Window->new(
    -title => "Plugin Browser",
    -left  => CW_USEDEFAULT,
    -size  => [560,440],
    -maxsize => [560,440],
    -dialogui => 1,
);

my $icon = new Win32::GUI::Icon('QAR.ICO');
$mw->SetIcon($icon);

$mw->AddLabel(
	-text => "",
	-name => "biglabel1",
	-pos => [10,10],
	-size => [530,40],
	-sunken => 1
);

$mw->AddLabel(
	-text => "Plugin Dir: ",
	-pos  => [20,23],

);

my $plugindirtext = $mw->AddTextfield(
	-name     => "plugindir",
  -tabstop  => 1,
  -left     => 100,
  -top      => 18,
  -width    => 300,
  -height   => 25,
  -tabstop  => 1,
  -foreground => "#000000",
  -background => "#FFFFFF"
);

my $browse = $mw->AddButton(
	-name    => 'browse',
  -text    => 'Browse',
  -size    => [50,25],
  -pos     => [450,18],
);

my $datatab = $mw->AddTabStrip(
	-pos => [10,60],
	-size => [530,280],
	-name => "datatab"
);

$datatab->InsertItem(-text => "Browse");
$datatab->InsertItem(-text => "Plugin File");

my $lb1 = $mw->AddListbox(
    -name    => 'LB1',
    -pos     => [20,100],
    -size    => [180,240],
    -multisel => 2,
    -vscroll => 1
);

my $gb1 = $mw->AddGroupbox(
    -name  => 'GB',
    -title => 'Plugin Info',
    -pos   => [260,100],
    -size  => [260,220],
);

my $gblbl = $mw->AddLabel(
    -name   => 'LBL',
    -left   => $mw->GB->Left()+10,
    -top    => $mw->GB->Top()+20,
    -width  => $mw->GB->ScaleWidth()-20,
    -height => $mw->GB->ScaleHeight()-40,
);

# The following elements go on the "Plugin File" tab and
# are initially hidden
my $lb2 = $mw->AddListbox(
    -name    => 'LB2',
    -pos     => [320,100],
    -size    => [200,240],
    -vscroll => 1,
    -multisel => 2
#    -onSelChange => \&newSelection,
);
$lb2->Hide();

my $add = $mw->AddButton(
    -name    => 'Add',
    -text    => '>>',
    -tip     => "Add Plugin",
    -size    => [50,25],
    -pos     => [230,130],
);
$add->Hide();

my $remove = $mw->AddButton(
    -name    => 'Remove',
    -text    => '<<',
    -tip     => "Remove Plugin",
    -size    => [50,25],
    -pos     => [230,180],
);
$remove->Hide();

my $open = $mw->AddButton(
    -name    => 'Open',
    -tip     => "Open Plugin File",
    -text    => 'Open',
    -size    => [50,25],
    -pos     => [230,230],
);
$open->Hide();

my $save = $mw->AddButton(
    -name    => 'Save',
    -tip     => "Save Plugin File",
    -text    => 'Save',
    -size    => [50,25],
    -pos     => [230,280],
);
$save->Hide();

$mw->AddButton(
    -name    => 'BT',
    -text    => 'Exit',
    -size    => [50,25],
    -pos     => [450,350],
    -onClick => sub{-1;},
);

my $status = new Win32::GUI::StatusBar($mw,
		-text  => "copyright 2010 Quantum Analytics Research, LLC",
);

$mw->Show();
Win32::GUI::Dialog();
$mw->Hide();
exit(0);

sub datatab_Click {
	if ($datatab->SelectedItem == 0) {
		$lb2->Hide();
		$add->Hide();
		$remove->Hide();
		$open->Hide();
		$save->Hide();
		$gb1->Show();
		$gblbl->Show();
	}
	
	if ($datatab->SelectedItem == 1) {
		$lb2->Show();
		$add->Show();
		$remove->Show();
		$open->Show();
		$save->Show();
		$gb1->Hide();
		$gblbl->Hide();
	}
}

sub browse_Click {
	$plugindir = Win32::GUI::BrowseForFolder(
  							-title => "Report Dir",
                -root => 0x0011,
                -folderonly => 1,
                -includefiles => 0,
        );
	$plugindir = $plugindir."\\" unless $plugindir =~ m/\\$/;
	$plugindirtext->Text("");
  $plugindirtext->Text($plugindir);
  
  $mw->LB1->ResetContent();
	my @plugins;
	opendir(DIR,$plugindir);
	push(@plugins, grep(/\.pl$/,readdir(DIR)));
	closedir(DIR);
	$mw->LB1->Add(sort @plugins);
  0;
}

sub LB1_SelChange {
	if ($datatab->SelectedItem == 0) {
		\&newSelection();
	}	
}

sub newSelection {
	my $lb = shift;
# Set the label text to reflect the change
	my $item = $lb1->GetCurSel();
	my $text = $lb1->GetText($item);
	$lb1->GetParent()->LBL->Text(get_plugin_info($text));
	return 1;
}
	
sub get_plugin_info {
	my $name = shift;
	require $plugindir."\\".$name;
	$name =~ s/\.pl$//;
	my $text = "Plugin Name:  ".$name."\r\n";
	eval {
		$text .= "Version: ".$name->getVersion."\r\n";
	};
	
	eval {
		$text .= "Hive   : ".$name->getHive."\r\n\r\n";
	};
	
	eval {
		$text .= "Descr  : \r\n";
		$text .= $name->getShortDescr."\r\n";
	};
	return $text;
}

sub Add_Click {
	my @list = $lb1->SelectedItems();
	foreach my $i (sort {$a <=> $b} @list) {
		my $str = $lb1->GetString($i);
		$str =~ s/\.pl$//;
		$lb2->InsertString($str);
	}
}

#-----------------------------------------------------------
# Note regarding use of DeleteString(); if starting from index
# 0 and increasing, the index changes so that after the first 
# index item is deleted, the second index item is reset.  To
# avoid this issue, reverse the order of the indexes.
#-----------------------------------------------------------
sub Remove_Click {
	my @list = $lb2->SelectedItems();
	foreach my $i (reverse @list) {
		$lb2->DeleteString($i);
	}
}

sub Save_Click {
	my $file = Win32::GUI::GetSaveFileName(
		-owner => $mw,
		-title => "Save Plugin File",
		-explorer => 1,
		-directory => $plugindir,
		-filter => ['All files' => '*.*']
	);
	
	if ($file) {
  	$file =~ s/\.\w+$//;
  }
  elsif (Win32::GUI::CommDlgExtendedError()) {
     $mw->MessageBox ("ERROR : ".Win32::GUI::CommDlgExtendedError(),
                        "GetSaveFileName Error");
  }
  
  open(FH,">",$file);
  print FH "# Plugin file created via Plugin Browser\n";
  print FH "# Date: ".localtime(time)."\n";
  print FH "# User: ".$ENV{USERNAME}."\n";
  print FH "#\n";
  print FH "\n";  
  my $count = $lb2->GetCount();
  foreach my $i (0..$count - 1) {
  	my $str = $lb2->GetString($i);
  	print FH $str."\n";
  }
  close(FH); 
  $lb2->ResetContent(); 
  0;
}

sub Open_Click {
	my $file = Win32::GUI::GetOpenFileName(
		-owner => $mw,
		-title => "Open Plugin File",
		-explorer => 1,
		-directory => $plugindir,
		-filter => ['All files' => '*.*']
	);
	
	if ($file) {
  	open(FH,"<",$file);
  	while(<FH>) {
  		chomp;
  		$lb2->InsertString($_);
  	}
  	close(FH);
  }
  elsif (Win32::GUI::CommDlgExtendedError()) {
     $mw->MessageBox ("ERROR : ".Win32::GUI::CommDlgExtendedError(),
                        "GetSaveFileName Error");
  }
}