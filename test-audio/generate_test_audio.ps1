Add-Type -AssemblyName System.Speech
$voice = New-Object System.Speech.Synthesis.SpeechSynthesizer
$voice.Rate = -1
$voice.Volume = 100
$samples = @(
  @{ Name = 'short_work.wav'; Text = 'I want to practice a conversation about my work.' },
  @{ Name = 'short_meeting.wav'; Text = 'Tomorrow I have a meeting with my manager.' },
  @{ Name = 'interrupt_wait.wav'; Text = 'Wait, I want to say something.' },
  @{ Name = 'interrupt_change_topic.wav'; Text = 'Stop for a moment, I want to change the topic.' },
  @{ Name = 'long_project_update.wav'; Text = 'I want to practice a conversation about my work because tomorrow I have a meeting with my manager and I need to explain the progress of my project, the problems we found, and the next steps for the team.' },
  @{ Name = 'long_client_call.wav'; Text = 'In my next client call, I need to describe the current status of the application, explain why some features took longer than expected, ask a few questions about priorities, and make sure the client feels confident about the plan.' },
  @{ Name = 'very_long_work_story.wav'; Text = 'I would like to practice a longer answer in English. Last week I was working on an important task with several technical details. At first, everything looked simple, but then we discovered a problem with the integration, so I had to review the logs, compare different scenarios, explain the situation to my team, and propose a solution that was clear, practical, and easy to test.' },
  @{ Name = 'very_long_meeting_explanation.wav'; Text = 'During the meeting, I want to explain that the project is moving forward, but we still need to validate a few important details before the final release. I also want to mention that communication has improved, that the team is solving problems faster, and that I am preparing a short summary with the risks, the completed tasks, and the next actions for this week.' }
)
foreach ($sample in $samples) {
  $path = Join-Path 'C:\englishcar\test-audio' $sample.Name
  $voice.SetOutputToWaveFile($path)
  $voice.Speak($sample.Text)
  $voice.SetOutputToNull()
  Write-Host "Created $path"
}
$voice.Dispose()
