from gym.envs.registration import register


register(
    id='troubleshooting-v0',
    entry_point='gym_troubleshooting.envs:TroubleshootingEnv',
)
