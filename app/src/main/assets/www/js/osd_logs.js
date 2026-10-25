
function get_filelist() {
    $.ajax({url:"/logs",success:populate_filelist});
};

function populate_filelist(dataStr) {
   var dataObj = (typeof dataStr === 'string') ? JSON.parse(dataStr) : dataStr;
   $("#logfilelist").empty();
   $("#logfilelist").append("<h2>Available Log Files</h2>");
   $("#logfilelist").append("<ul>");

   $.each(dataObj['logFileList'], function(index, value) {
       $("#logfilelist").append('<li><a href="/logs/' + value + '">' + value + '</a></li>');
   });
   $("#logfilelist").append("</ul>");
}

$(document).ready(function() {  
    get_filelist();
});
